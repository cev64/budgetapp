package com.personal.budget.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.personal.budget.data.local.BudgetDatabase
import com.personal.budget.data.local.PrefsStore
import com.personal.budget.data.local.TransactionEntity
import com.personal.budget.data.remote.RestApi
import com.personal.budget.data.remote.SupabaseConfig
import com.personal.budget.data.remote.TokenProvider
import com.personal.budget.data.remote.defaultHttpClient
import com.personal.budget.data.repository.AuthRepository
import com.personal.budget.data.repository.SyncEngine
import com.personal.budget.data.repository.SyncResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * SyncEngine against a fake PostgREST (MockWebServer): push confirms only unchanged rows,
 * pull never overwrites dirty rows, tombstones are kept, pages continue past 1000 rows, the
 * cursor is stored, and a 401 triggers exactly one token refresh.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncEngineTest {
    private lateinit var server: MockWebServer
    private lateinit var db: BudgetDatabase
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private var onPostTransactions: (() -> Unit)? = null
    private var remoteTransactions: List<String> = emptyList()
    private var expiredOnce = false
    private var refreshes = 0

    private val tokens = object : TokenProvider {
        var token = "t0"
        override suspend fun accessToken() = token
        override suspend fun refreshAfterUnauthorized(rejectedToken: String): String {
            refreshes++
            token = "t1"
            return token
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val url = request.url
                val path = url.encodedPath
                if (expiredOnce && request.headers["Authorization"] == "Bearer t0") {
                    return MockResponse.Builder().code(401).body("""{"message":"JWT expired"}""").build()
                }
                if (request.method == "POST" && path.endsWith("/transactions")) {
                    onPostTransactions?.invoke()
                    val sent = Json.parseToJsonElement(request.body!!.utf8()).jsonArray
                    val echoed = JsonArray(sent.map { o ->
                        kotlinx.serialization.json.JsonObject(o.jsonObject + ("updated_at" to kotlinx.serialization.json.JsonPrimitive("2026-10-03T10:00:00.000001+00:00")))
                    })
                    return MockResponse.Builder().code(201).body(echoed.toString()).build()
                }
                if (request.method == "POST") return MockResponse.Builder().code(201).body("[]").build()
                if (path.endsWith("/transactions")) {
                    val offset = url.queryParameter("offset")?.toInt() ?: 0
                    val filter = url.queryParameter("updated_at")
                    val all = remoteTransactions
                    val page = if (filter != null && filter.startsWith("gte.")) all.drop(1000).drop(offset) else all.drop(offset)
                    return MockResponse.Builder().code(200).body("[" + page.take(1000).joinToString(",") + "]").build()
                }
                return MockResponse.Builder().code(200).body("[]").build()
            }
        }
        server.start()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, BudgetDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private fun engine(): SyncEngine {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = SupabaseConfig(server.url("/").toString().trimEnd('/'), "sb_publishable_test")
        val rest = RestApi(config, defaultHttpClient(), tokens)
        val auth = object : AuthRepositoryLike {}
        return SyncEngine(db, rest, auth.real(config, ctx), PrefsStore(ctx))
    }

    private fun txnJson(id: String, ts: String, amount: Double, deleted: Boolean = false) =
        """{"id":"$id","user_id":"u","year":2026,"month":10,"category_id":"c","date":null,"item":"x","amount":$amount,"note":null,"created_at":"$ts","updated_at":"$ts","deleted":$deleted}"""

    @Test
    fun editDuringInFlightPush_staysDirty() = runBlocking {
        val dao = db.transactionDao()
        dao.upsert(listOf(TransactionEntity("a", 2026, 10, "c", item = "first", amount = 1.0, dirty = true, localVersion = 1)))
        dao.upsert(listOf(TransactionEntity("b", 2026, 10, "c", item = "other", amount = 2.0, dirty = true, localVersion = 1)))
        // While the POST is on the wire, the user edits row "a" (version 1 -> 2).
        onPostTransactions = {
            runBlocking { dao.upsert(listOf(dao.get("a")!!.copy(item = "edited", dirty = true, localVersion = 2))) }
        }
        assertEquals(SyncResult.Success, engine().sync())
        val a = dao.get("a")!!
        val b = dao.get("b")!!
        assertTrue("edited mid-push: must stay dirty", a.dirty)
        assertEquals("edited", a.item)
        assertFalse("unchanged row is confirmed", b.dirty)
        assertEquals("2026-10-03T10:00:00.000001+00:00", b.updatedAt)
        // Push body never carries updated_at but always user_id.
        val post = requests.first { it.method == "POST" && it.url.encodedPath.endsWith("/transactions") }
        val first = Json.parseToJsonElement(post.body!!.utf8()).jsonArray[0].jsonObject
        assertFalse(first.containsKey("updated_at"))
        assertTrue(first.containsKey("user_id"))
        assertEquals("resolution=merge-duplicates,return=representation", post.headers["Prefer"])
        assertEquals("id", post.url.queryParameter("on_conflict"))
    }

    @Test
    fun pull_skipsDirtyRows_keepsTombstones_pagesAndStoresCursor() = runBlocking {
        val dao = db.transactionDao()
        dao.upsert(listOf(TransactionEntity("dirty", 2026, 10, "c", item = "local", amount = 9.0, dirty = true, localVersion = 5)))
        onPostTransactions = { // remote copy of the dirty row arrives in the same sync: must not win
        }
        remoteTransactions = (0 until 1500).map { i ->
            val ts = "2026-10-01T00:%02d:%02d.000000+00:00".format(i / 60 % 60, i % 60)
            when (i) {
                3 -> txnJson("gone", ts, 4.0, deleted = true)
                else -> txnJson("r$i", ts, i.toDouble())
            }
        } + txnJson("dirty", "2026-10-01T01:00:00.000000+00:00", 1.0)
        // The dirty row is pushed first and confirmed, so to test "pull never overwrites dirty rows"
        // make the push edit it again mid-flight (it stays dirty through the pull).
        onPostTransactions = {
            runBlocking { dao.upsert(listOf(dao.get("dirty")!!.copy(item = "local-2", localVersion = 6, dirty = true))) }
        }
        assertEquals(SyncResult.Success, engine().sync())
        assertEquals("local-2", dao.get("dirty")!!.item)
        assertTrue(dao.get("gone")!!.deleted)
        assertFalse(dao.all().any { it.id == "gone" })
        assertTrue(dao.get("r1499") != null)
        val pulls = requests.filter { it.method == "GET" && it.url.encodedPath.endsWith("/transactions") }
        assertTrue("paged past 1000 rows", pulls.size >= 2)
        assertTrue(db.syncStateDao().cursor("transactions")!!.startsWith("2026-10-01T"))
        // A second sync starts from cursor − 10 s with gt.
        requests.clear()
        engine().sync()
        val next = requests.first { it.method == "GET" && it.url.encodedPath.endsWith("/transactions") }
        assertTrue(next.url.queryParameter("updated_at")!!.startsWith("gt.2026-10-01T00:59:5"))
    }

    @Test
    fun unauthorized_refreshesOnce_andRetries() = runBlocking {
        expiredOnce = true
        db.transactionDao().upsert(listOf(TransactionEntity("a", 2026, 10, "c", amount = 1.0, dirty = true, localVersion = 1)))
        assertEquals(SyncResult.Success, engine().sync())
        assertEquals(1, refreshes)
        assertFalse(db.transactionDao().get("a")!!.dirty)
    }
}

/** Builds a real AuthRepository that already holds a session for user "u". */
private interface AuthRepositoryLike {
    fun real(config: SupabaseConfig, ctx: android.content.Context): AuthRepository {
        val store = com.personal.budget.data.local.AuthStore(ctx)
        runBlocking {
            store.save(com.personal.budget.data.local.StoredSession("t0", "r0", Long.MAX_VALUE / 2, "u", "u@example.com"))
        }
        val repo = AuthRepository(config, com.personal.budget.data.remote.AuthApi(config, defaultHttpClient()), store, PrefsStore(ctx))
        runBlocking { repo.init() }
        return repo
    }
}
