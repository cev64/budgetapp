package com.personal.budget.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.personal.budget.data.local.AuthStore
import com.personal.budget.data.local.BudgetDatabase
import com.personal.budget.data.local.PrefsStore
import com.personal.budget.data.remote.AuthApi
import com.personal.budget.data.remote.JSON_MEDIA
import com.personal.budget.data.remote.RestApi
import com.personal.budget.data.remote.SupabaseConfig
import com.personal.budget.data.remote.defaultHttpClient
import com.personal.budget.data.remote.parseJson
import com.personal.budget.data.remote.send
import com.personal.budget.data.repository.AuthRepository
import com.personal.budget.data.repository.BudgetRepository
import com.personal.budget.data.repository.SyncEngine
import com.personal.budget.data.repository.SyncResult
import com.personal.budget.domain.model.MonthKey
import com.personal.budget.domain.model.Txn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Live end-to-end check against the real Supabase project (config/supabase.json, via BuildConfig).
 * SKIPPED unless BUDGET_LIVE_EMAIL and BUDGET_LIVE_PASSWORD are set; never commit credentials.
 *
 *   BUDGET_LIVE_EMAIL=… BUDGET_LIVE_PASSWORD=… ./gradlew testDebugUnitTest --tests '*LiveSyncTest*' --rerun
 *
 * Expects a confirmed account that already holds the synthetic fixture (14 categories, Jan 2026)
 * and a "Checking" account. Everything the test writes is tombstoned / restored at the end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveSyncTest {
    private val email: String? = System.getenv("BUDGET_LIVE_EMAIL")?.takeIf { it.isNotBlank() }
    private val password: String? = System.getenv("BUDGET_LIVE_PASSWORD")?.takeIf { it.isNotBlank() }

    @Test
    fun liveEndToEnd() = runBlocking {
        assumeTrue("live credentials not set", email != null && password != null)
        val config = SupabaseConfig.fromBuildConfig()
        assumeTrue("backend not configured in this build", config.isConfigured)

        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val http = defaultHttpClient()
        val prefs = PrefsStore(ctx)
        val auth = AuthRepository(config, AuthApi(config, http), AuthStore(ctx), prefs)
        auth.init()
        auth.adopt(auth.signIn(email!!, password!!))
        val userId = auth.currentUserId()!!
        val db = Room.inMemoryDatabaseBuilder(ctx, BudgetDatabase::class.java).allowMainThreadQueries().build()
        val repo = BudgetRepository(db)
        val engine = SyncEngine(db, RestApi(config, http, auth), auth, prefs)

        suspend fun get(path: String): JsonArray {
            val res = http.send(
                Request.Builder().url("${config.url}/rest/v1/$path")
                    .header("apikey", config.publishableKey)
                    .header("Authorization", "Bearer ${auth.accessToken()}")
                    .get().build(),
            )
            assertTrue("GET $path -> HTTP ${res.code}", res.code in 200..299)
            return parseJson(res.body) as JsonArray
        }

        suspend fun latestSnapshotNetWorth(): Double =
            get("net_worth_snapshots?select=taken_on,net_worth&deleted=eq.false&order=taken_on.desc&limit=1")[0]
                .jsonObject.getValue("net_worth").jsonPrimitive.double

        var myTxnId: String? = null
        var otherTxnId: String? = null
        var checkingId: String? = null
        var originalBalance: Double? = null
        try {
            // 1. Sign in + full pull --------------------------------------------------------------
            assertEquals(SyncResult.Success, engine.sync())
            val snap = repo.snapshot.first()
            val cats = snap.book.categories
            println("LIVE 1: pulled ${cats.size} categories, ${snap.book.months.size} months, ${snap.transactions.size} transactions, ${snap.accounts.size} accounts, ${snap.netWorthHistory.size} snapshots")
            assertEquals("live categories", 14, cats.size)
            val jan = snap.book.monthSummary(MonthKey(2026, 1))
            val food = cats.single { it.name == "Food" }
            val foodActual = snap.book.actual(MonthKey(2026, 1), food.id) ?: 0.0
            println("LIVE 1: Jan 2026 income actual=${jan.actual.income}, Food actual=$foodActual")
            assertEquals(2100.0, jan.actual.income, 0.005)
            assertTrue("Food actual >= 120", foodActual >= 120.0)

            // 2. Local transaction -> push -> server has it with my user_id -----------------------
            val fun_ = cats.single { it.name == "Fun" }
            myTxnId = repo.saveTransaction(Txn("", 2026, 1, fun_.id, "2026-01-20", "Android live test", 3.21))
            assertEquals(SyncResult.Success, engine.sync())
            val serverTxn = get("transactions?id=eq.$myTxnId&select=*").single().jsonObject
            assertEquals(userId, serverTxn.getValue("user_id").jsonPrimitive.content)
            assertEquals(3.21, serverTxn.getValue("amount").jsonPrimitive.double, 0.0001)
            assertTrue("local row confirmed clean", !db.transactionDao().get(myTxnId)!!.dirty)
            println("LIVE 2: pushed transaction confirmed on server with the signed-in user_id")

            // 3. Balance edit -> push -> snapshot RPC ran (+100) ---------------------------------
            val checking = repo.snapshot.first().accounts.single { it.name == "Checking" }
            checkingId = checking.id
            originalBalance = checking.balance
            auth.accessToken()
            RestApi(config, http, auth).rpc("take_net_worth_snapshot") // baseline = current server state
            val before = latestSnapshotNetWorth()
            repo.setAccountBalance(checking.id, 2700.0)
            assertEquals(SyncResult.Success, engine.sync())
            val after = latestSnapshotNetWorth()
            println("LIVE 3: Checking ${checking.balance} -> 2700; snapshot net worth $before -> $after")
            assertEquals(2700.0 - checking.balance, after - before, 0.005)
            val localLatest = repo.snapshot.first().netWorthHistory.last()
            assertEquals("snapshot pulled into Room", after, localLatest.netWorth, 0.005)

            // 4. Row written by "another device" lands in Room ----------------------------------
            otherTxnId = UUID.randomUUID().toString()
            val body = JsonArray(
                listOf(
                    buildJsonObject {
                        put("id", otherTxnId)
                        put("user_id", userId)
                        put("year", 2026)
                        put("month", 1)
                        put("category_id", fun_.id)
                        put("date", "2026-01-21")
                        put("item", "Android live test (other device)")
                        put("amount", 1.23)
                        put("deleted", false)
                    },
                ),
            )
            val ins = http.send(
                Request.Builder().url("${config.url}/rest/v1/transactions")
                    .header("apikey", config.publishableKey)
                    .header("Authorization", "Bearer ${auth.accessToken()}")
                    .header("Prefer", "return=minimal")
                    .post(body.toString().toRequestBody(JSON_MEDIA)).build(),
            )
            assertTrue("REST insert -> HTTP ${ins.code}", ins.code in 200..299)
            assertEquals(SyncResult.Success, engine.sync())
            val landed = db.transactionDao().get(otherTxnId)
            assertNotNull("other-device row pulled into Room", landed)
            assertEquals(1.23, landed!!.amount, 0.0001)
            println("LIVE 4: row inserted via REST was pulled into Room")

            // 5. Tombstone -> server row deleted = true -----------------------------------------
            repo.deleteTransaction(myTxnId)
            assertEquals(SyncResult.Success, engine.sync())
            val deleted = get("transactions?id=eq.$myTxnId&select=id,deleted").single().jsonObject
            assertTrue(deleted.getValue("deleted").jsonPrimitive.boolean)
            println("LIVE 5: tombstone reached the server (deleted = true)")
        } finally {
            // 6. Cleanup: tombstone test rows, restore Checking -----------------------------------
            otherTxnId?.let { if (db.transactionDao().get(it) != null) repo.deleteTransaction(it) }
            myTxnId?.let { if (db.transactionDao().get(it)?.deleted == false) repo.deleteTransaction(it) }
            if (checkingId != null && originalBalance != null) repo.setAccountBalance(checkingId!!, originalBalance!!)
            val cleanup = engine.sync()
            println("LIVE 6: cleanup sync=$cleanup")
            db.close()
        }
        // Verify cleanup on the server.
        val restored = RestApi(config, http, auth).select("accounts", listOf("id" to "eq.$checkingId")).single()
        assertEquals(originalBalance!!, restored.getValue("balance").jsonPrimitive.double, 0.005)
        val other = RestApi(config, http, auth).select("transactions", listOf("id" to "eq.$otherTxnId")).single()
        assertTrue(other.getValue("deleted").jsonPrimitive.boolean)
        println("LIVE 6: Checking restored to $originalBalance; other-device row tombstoned")
    }
}
