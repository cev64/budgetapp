package com.personal.budget.data.repository

import androidx.room.withTransaction
import com.personal.budget.data.local.BudgetDatabase
import com.personal.budget.data.local.BudgetEntity
import com.personal.budget.data.local.MonthEntity
import com.personal.budget.data.local.NetWorthSnapshotEntity
import com.personal.budget.data.local.PrefsStore
import com.personal.budget.data.local.SettingsEntity
import com.personal.budget.data.local.SyncCursorEntity
import com.personal.budget.data.remote.HttpException
import com.personal.budget.data.remote.MalformedResponseException
import com.personal.budget.data.remote.OfflineException
import com.personal.budget.data.remote.RemoteMappers
import com.personal.budget.data.remote.RestApi
import com.personal.budget.data.remote.SessionExpiredException
import com.personal.budget.data.remote.updatedAt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.OffsetDateTime

sealed interface SyncPhase {
    data object Idle : SyncPhase
    data object Syncing : SyncPhase
    data class Offline(val message: String = "Offline") : SyncPhase
    data class Error(val message: String) : SyncPhase
    data object NeedsSignIn : SyncPhase
}

sealed interface SyncResult {
    data object Success : SyncResult
    data object Skipped : SyncResult
    data class Offline(val message: String) : SyncResult
    data class Failed(val message: String) : SyncResult
    data object NeedsSignIn : SyncResult
}

/**
 * docs/SYNC.md, implemented exactly:
 *
 *  PUSH  (first) for each table in the documented order: send every dirty row (tombstones
 *        included) as a bulk upsert with `on_conflict` and
 *        `Prefer: resolution=merge-duplicates,return=representation`. For each returned row, clear
 *        `dirty` and store the server `updated_at` ONLY IF the row's local_version is still the
 *        version that was sent (conditional UPDATE … WHERE local_version = :sent). An edit made
 *        while the request was in flight bumped the version, so the row stays dirty and is pushed
 *        on the next run. If a batch is rejected (4xx), rows are retried one by one so a single
 *        bad row cannot block the rest; rejected rows stay dirty and are reported.
 *
 *  PULL  (second) per table: `updated_at=gt.(cursor − 10 s)&order=updated_at.asc&limit=1000`.
 *        Pages continue with `updated_at=gte.<max of previous page>` until a page has < 1000 rows
 *        (if a whole page shares one timestamp, `offset` is used to step past it). Re-reading
 *        overlapping rows is harmless. The cursor (max updated_at seen) is saved after each page.
 *        Each page is applied in one DB transaction that re-reads the local rows and skips any
 *        that are dirty, so a remote row never overwrites an unpushed local edit, even one made
 *        after the page was fetched. Tombstones are stored locally (deleted = 1) and hidden by
 *        every UI query.
 *
 * Only one sync runs at a time ([mutex]); requests that arrive meanwhile coalesce into one rerun.
 */
class SyncEngine(
    private val db: BudgetDatabase,
    private val rest: RestApi,
    private val auth: AuthRepository,
    private val prefs: PrefsStore,
    private val onDataChanged: suspend () -> Unit = {},
) {
    private val mutex = Mutex()
    @Volatile private var rerunRequested = false

    /** A net-worth snapshot should be taken on the next successful push (set by imports and failed RPCs). */
    @Volatile private var snapshotRequested = false

    fun requestNetWorthSnapshot() {
        snapshotRequested = true
    }
    private val _phase = MutableStateFlow<SyncPhase>(SyncPhase.Idle)
    val phase: StateFlow<SyncPhase> = _phase.asStateFlow()

    /** Runs a full push+pull. Safe to call from anywhere; concurrent calls coalesce. */
    suspend fun sync(): SyncResult {
        if (!auth.isConfigured) return SyncResult.Skipped
        // A process started only for the periodic worker must not race the async auth init.
        auth.awaitReady()
        if (!auth.hasSession()) {
            _phase.value = SyncPhase.NeedsSignIn
            return SyncResult.NeedsSignIn
        }
        if (!mutex.tryLock()) {
            rerunRequested = true
            return SyncResult.Skipped
        }
        try {
            var result: SyncResult
            do {
                rerunRequested = false
                result = runOnce()
            } while (rerunRequested && result == SyncResult.Success)
            return result
        } finally {
            mutex.unlock()
        }
    }

    /** Runs [block] while no sync can start or be in progress (used to wipe data at sign-out). */
    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }

    /** Push only (used before sign-out). Returns rows still unsynced afterwards. */
    suspend fun pushOnly(): Int = mutex.withLock {
        runCatching { push() }
        db.syncStateDao().pendingCount()
    }

    private suspend fun runOnce(): SyncResult {
        _phase.value = SyncPhase.Syncing
        return try {
            val affectsNetWorth = netWorthInputsDirty()
            val rejected = push()
            if (affectsNetWorth || snapshotRequested) takeNetWorthSnapshot()
            val changed = pull()
            prefs.setLastSyncAt(System.currentTimeMillis())
            if (changed > 0) onDataChanged()
            if (rejected > 0) {
                val msg = "$rejected change${if (rejected == 1) "" else "s"} rejected by the server"
                _phase.value = SyncPhase.Error(msg)
                SyncResult.Failed(msg)
            } else {
                _phase.value = SyncPhase.Idle
                SyncResult.Success
            }
        } catch (e: CancellationException) {
            _phase.value = SyncPhase.Idle
            throw e
        } catch (e: SessionExpiredException) {
            _phase.value = SyncPhase.NeedsSignIn
            SyncResult.NeedsSignIn
        } catch (e: OfflineException) {
            _phase.value = SyncPhase.Offline()
            SyncResult.Offline(e.message ?: "Offline")
        } catch (e: HttpException) {
            val msg = if (e.code == 429) "Server busy, will retry" else e.message
            _phase.value = SyncPhase.Error(msg)
            SyncResult.Failed(msg)
        } catch (e: MalformedResponseException) {
            _phase.value = SyncPhase.Error(e.message ?: "Unexpected server response")
            SyncResult.Failed(e.message ?: "Unexpected server response")
        } catch (e: Exception) {
            _phase.value = SyncPhase.Error(e.message ?: e.javaClass.simpleName)
            SyncResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * DOMAIN_RULES §5b: a push that includes accounts, ledger entries or budgets of an
     * account-linked category changes net worth, so the server is asked to re-snapshot today.
     */
    private suspend fun netWorthInputsDirty(): Boolean {
        if (db.accountDao().dirty().isNotEmpty() || db.ledgerEntryDao().dirty().isNotEmpty()) return true
        val dirtyBudgets = db.budgetDao().dirty()
        if (dirtyBudgets.isEmpty()) return false
        val linked = db.accountDao().all().mapNotNull { it.linkedCategoryId }.toSet()
        return dirtyBudgets.any { it.categoryId in linked }
    }

    /** POST rpc/take_net_worth_snapshot; the returned row is stored right away (the pull confirms it). */
    private suspend fun takeNetWorthSnapshot() {
        snapshotRequested = true
        val rows = rest.rpc("take_net_worth_snapshot")
        snapshotRequested = false
        val dao = db.netWorthSnapshotDao()
        db.withTransaction {
            val remote = rows.map(RemoteMappers::snapshot)
            val locals = dao.byKeys(remote.map { it.takenOn }).associateBy { it.takenOn }
            dao.upsert(remote.filter { locals[it.takenOn]?.dirty != true }.map { it.copy(localVersion = locals[it.takenOn]?.localVersion ?: 0) })
        }
    }

    // ------------------------------------------------------------------------------------------
    // Table descriptors
    // ------------------------------------------------------------------------------------------

    /**
     * [order] adds the primary key as a tiebreak after updated_at, so `offset` paging over rows that
     * share one timestamp sees a stable order between requests.
     */
    private abstract class Table<E>(val name: String, val onConflict: String, val order: String = "updated_at.asc,id.asc") {
        abstract suspend fun dirty(): List<E>
        abstract fun toJson(e: E, userId: String): JsonObject
        abstract fun key(e: E): String
        abstract fun remoteKey(o: JsonObject): String
        abstract fun version(e: E): Long
        abstract suspend fun markClean(e: E, version: Long, updatedAt: String?): Int
        /** Applies one pulled page; must skip dirty local rows. Returns rows written. */
        abstract suspend fun apply(rows: List<JsonObject>): Int
    }

    private fun nowIso() = Instant.now().toString()

    private val tables: List<Table<*>> by lazy {
        val settingsDao = db.settingsDao()
        val categoryDao = db.categoryDao()
        val monthDao = db.monthDao()
        val budgetDao = db.budgetDao()
        val accountDao = db.accountDao()
        val recurringDao = db.recurringItemDao()
        val ledgerDao = db.ledgerEntryDao()
        val txnDao = db.transactionDao()
        val snapshotDao = db.netWorthSnapshotDao()

        listOf(
            object : Table<SettingsEntity>("settings", "user_id", "updated_at.asc,user_id.asc") {
                override suspend fun dirty() = settingsDao.dirty()
                override fun toJson(e: SettingsEntity, userId: String) = RemoteMappers.toJson(e, userId)
                override fun key(e: SettingsEntity) = "settings"
                override fun remoteKey(o: JsonObject) = "settings"
                override fun version(e: SettingsEntity) = e.localVersion
                override suspend fun markClean(e: SettingsEntity, version: Long, updatedAt: String?) = settingsDao.markClean(version, updatedAt)
                override suspend fun apply(rows: List<JsonObject>): Int {
                    val remote = rows.lastOrNull()?.let(RemoteMappers::settings) ?: return 0
                    val local = settingsDao.get()
                    if (local?.dirty == true) return 0
                    settingsDao.upsert(listOf(remote.copy(localVersion = local?.localVersion ?: 0)))
                    return 1
                }
            },
            idTable("categories", { categoryDao.dirty() }, RemoteMappers::toJson, { it.id }, { it.localVersion },
                { e, v, u -> categoryDao.markClean(e.id, v, u) }, RemoteMappers::category, { categoryDao.byKeys(it) },
                { it.dirty }, { r, l -> r.copy(localVersion = l?.localVersion ?: 0) }, { categoryDao.upsert(it) }),
            object : Table<MonthEntity>("months", "user_id,year,month", "updated_at.asc,year.asc,month.asc") {
                override suspend fun dirty() = monthDao.dirty()
                override fun toJson(e: MonthEntity, userId: String) = RemoteMappers.toJson(e, userId)
                override fun key(e: MonthEntity) = "${e.year}-${e.month}"
                override fun remoteKey(o: JsonObject) = RemoteMappers.month(o).let { "${it.year}-${it.month}" }
                override fun version(e: MonthEntity) = e.localVersion
                override suspend fun markClean(e: MonthEntity, version: Long, updatedAt: String?) =
                    monthDao.markClean(e.year, e.month, version, updatedAt)
                override suspend fun apply(rows: List<JsonObject>): Int {
                    val out = rows.map(RemoteMappers::month).mapNotNull { r ->
                        val l = monthDao.get(r.year, r.month)
                        if (l?.dirty == true) null else r.copy(localVersion = l?.localVersion ?: 0)
                    }
                    monthDao.upsert(out)
                    return out.size
                }
            },
            object : Table<BudgetEntity>("budgets", "user_id,year,month,category_id", "updated_at.asc,year.asc,month.asc,category_id.asc") {
                override suspend fun dirty() = budgetDao.dirty()
                override fun toJson(e: BudgetEntity, userId: String) = RemoteMappers.toJson(e, userId)
                override fun key(e: BudgetEntity) = "${e.year}-${e.month}-${e.categoryId}"
                override fun remoteKey(o: JsonObject) = RemoteMappers.budget(o).let { "${it.year}-${it.month}-${it.categoryId}" }
                override fun version(e: BudgetEntity) = e.localVersion
                override suspend fun markClean(e: BudgetEntity, version: Long, updatedAt: String?) =
                    budgetDao.markClean(e.year, e.month, e.categoryId, version, updatedAt)
                override suspend fun apply(rows: List<JsonObject>): Int {
                    val out = rows.map(RemoteMappers::budget).mapNotNull { r ->
                        val l = budgetDao.get(r.year, r.month, r.categoryId)
                        if (l?.dirty == true) null else r.copy(localVersion = l?.localVersion ?: 0)
                    }
                    budgetDao.upsert(out)
                    return out.size
                }
            },
            idTable("accounts", { accountDao.dirty() }, RemoteMappers::toJson, { it.id }, { it.localVersion },
                { e, v, u -> accountDao.markClean(e.id, v, u) }, RemoteMappers::account, { accountDao.byKeys(it) },
                { it.dirty }, { r, l -> r.copy(localVersion = l?.localVersion ?: 0) }, { accountDao.upsert(it) }),
            idTable("recurring_items", { recurringDao.dirty() }, RemoteMappers::toJson, { it.id }, { it.localVersion },
                { e, v, u -> recurringDao.markClean(e.id, v, u) }, RemoteMappers::recurring, { recurringDao.byKeys(it) },
                { it.dirty }, { r, l -> r.copy(localVersion = l?.localVersion ?: 0) }, { recurringDao.upsert(it) }),
            idTable("ledger_entries", { ledgerDao.dirty() }, RemoteMappers::toJson, { it.id }, { it.localVersion },
                { e, v, u -> ledgerDao.markClean(e.id, v, u) }, RemoteMappers::ledgerEntry, { ledgerDao.byKeys(it) },
                { it.dirty }, { r, l -> r.copy(localVersion = l?.localVersion ?: 0) }, { ledgerDao.upsert(it) }),
            // Pull-only in practice: rows are dirty only after a backup import (source = "import").
            object : Table<NetWorthSnapshotEntity>("net_worth_snapshots", "user_id,taken_on", "updated_at.asc,taken_on.asc") {
                override suspend fun dirty() = snapshotDao.dirty()
                override fun toJson(e: NetWorthSnapshotEntity, userId: String) = RemoteMappers.toJson(e, userId)
                override fun key(e: NetWorthSnapshotEntity) = e.takenOn
                override fun remoteKey(o: JsonObject) = RemoteMappers.snapshot(o).takenOn
                override fun version(e: NetWorthSnapshotEntity) = e.localVersion
                override suspend fun markClean(e: NetWorthSnapshotEntity, version: Long, updatedAt: String?) =
                    snapshotDao.markClean(e.takenOn, version, updatedAt)
                override suspend fun apply(rows: List<JsonObject>): Int {
                    val remote = rows.map(RemoteMappers::snapshot)
                    val locals = snapshotDao.byKeys(remote.map { it.takenOn }).associateBy { it.takenOn }
                    val out = remote.filter { locals[it.takenOn]?.dirty != true }.map { it.copy(localVersion = locals[it.takenOn]?.localVersion ?: 0) }
                    snapshotDao.upsert(out)
                    return out.size
                }
            },
            idTable("transactions", { txnDao.dirty() }, { e, u -> RemoteMappers.toJson(e, u, nowIso()) }, { it.id }, { it.localVersion },
                { e, v, u -> txnDao.markClean(e.id, v, u) }, RemoteMappers::transaction, { txnDao.byKeys(it) },
                { it.dirty }, { r, l -> r.copy(localVersion = l?.localVersion ?: 0) }, { txnDao.upsert(it) }),
        )
    }

    /** Descriptor for the tables keyed by a client-generated uuid `id`. */
    private fun <E> idTable(
        name: String,
        dirtyFn: suspend () -> List<E>,
        toJsonFn: (E, String) -> JsonObject,
        idFn: (E) -> String,
        versionFn: (E) -> Long,
        markCleanFn: suspend (E, Long, String?) -> Int,
        fromJsonFn: (JsonObject) -> E,
        byKeysFn: suspend (List<String>) -> List<E>,
        isDirtyFn: (E) -> Boolean,
        keepVersionFn: (E, E?) -> E,
        upsertFn: suspend (List<E>) -> Unit,
    ): Table<E> = object : Table<E>(name, "id") {
        override suspend fun dirty() = dirtyFn()
        override fun toJson(e: E, userId: String) = toJsonFn(e, userId)
        override fun key(e: E) = idFn(e)
        override fun remoteKey(o: JsonObject) = idFn(fromJsonFn(o))
        override fun version(e: E) = versionFn(e)
        override suspend fun markClean(e: E, version: Long, updatedAt: String?) = markCleanFn(e, version, updatedAt)
        override suspend fun apply(rows: List<JsonObject>): Int {
            val remote = rows.map(fromJsonFn)
            val locals = remote.map(idFn).chunked(500).flatMap { byKeysFn(it) }.associateBy(idFn)
            val out = remote.mapNotNull { r ->
                val l = locals[idFn(r)]
                if (l != null && isDirtyFn(l)) null else keepVersionFn(r, l)
            }
            upsertFn(out)
            return out.size
        }
    }

    // ------------------------------------------------------------------------------------------
    // Push
    // ------------------------------------------------------------------------------------------

    /** Returns the number of rows the server rejected (they stay dirty). */
    private suspend fun push(): Int {
        val userId = auth.currentUserId() ?: throw SessionExpiredException("Not signed in")
        var rejected = 0
        for (table in tables) rejected += pushTable(table, userId)
        return rejected
    }

    private suspend fun <E> pushTable(table: Table<E>, userId: String): Int {
        val rows = table.dirty()
        if (rows.isEmpty()) return 0
        var rejected = 0
        for (chunk in rows.chunked(PUSH_BATCH)) {
            // Snapshot each row's version before the request leaves.
            val sent = chunk.map { it to table.version(it) }
            val body = JsonArray(chunk.map { table.toJson(it, userId) })
            val returned = try {
                rest.upsert(table.name, table.onConflict, body)
            } catch (e: HttpException) {
                if (e.code in 400..499 && e.code != 401 && e.code != 429 && chunk.size > 1) {
                    rejected += pushIndividually(table, sent, userId)
                    continue
                }
                if (e.code in 400..499 && e.code != 401 && e.code != 429) {
                    rejected += chunk.size
                    continue
                }
                throw e
            }
            confirm(table, sent, returned)
        }
        return rejected
    }

    private suspend fun <E> pushIndividually(table: Table<E>, sent: List<Pair<E, Long>>, userId: String): Int {
        var rejected = 0
        for ((row, version) in sent) {
            try {
                val returned = rest.upsert(table.name, table.onConflict, JsonArray(listOf(table.toJson(row, userId))))
                confirm(table, listOf(row to version), returned)
            } catch (e: HttpException) {
                if (e.code in 400..499 && e.code != 401 && e.code != 429) rejected++ else throw e
            }
        }
        return rejected
    }

    private suspend fun <E> confirm(table: Table<E>, sent: List<Pair<E, Long>>, returned: List<JsonObject>) {
        val byKey = returned.associateBy { table.remoteKey(it) }
        db.withTransaction {
            for ((row, version) in sent) {
                val server = byKey[table.key(row)] ?: continue // not confirmed: stays dirty
                table.markClean(row, version, server.updatedAt())
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Pull
    // ------------------------------------------------------------------------------------------

    /** Returns the number of local rows written. */
    private suspend fun pull(): Int {
        var written = 0
        for (table in tables) written += pullTable(table)
        return written
    }

    private suspend fun pullTable(table: Table<*>): Int {
        val stateDao = db.syncStateDao()
        val stored = stateDao.cursor(table.name)
        var maxSeen: Instant? = stored?.let(::parseTs)
        var filter: String? = maxSeen?.let { "gt." + it.minusSeconds(OVERLAP_SECONDS) }
        var pageAnchor: Instant? = null
        var offset = 0
        var written = 0
        while (true) {
            val params = buildList {
                filter?.let { add("updated_at" to it) }
                add("order" to table.order)
                add("limit" to PAGE_SIZE.toString())
                if (offset > 0) add("offset" to offset.toString())
            }
            val rows = rest.select(table.name, params)
            if (rows.isNotEmpty()) {
                written += db.withTransaction { table.apply(rows) }
            }
            val pageMax = rows.mapNotNull { it.updatedAt()?.let(::parseTs) }.maxOrNull()
            if (pageMax != null && (maxSeen == null || pageMax > maxSeen)) {
                maxSeen = pageMax
                stateDao.setCursor(SyncCursorEntity(table.name, pageMax.toString()))
            }
            if (rows.size < PAGE_SIZE || pageMax == null) break
            if (pageMax == pageAnchor) {
                // A whole page shares one timestamp: step past it with offset.
                offset += PAGE_SIZE
            } else {
                pageAnchor = pageMax
                filter = "gte.$pageMax"
                offset = 0
            }
        }
        return written
    }

    companion object {
        const val PAGE_SIZE = 1000
        const val PUSH_BATCH = 500
        const val OVERLAP_SECONDS = 10L

        /** PostgREST returns e.g. 2026-10-02T12:00:00.123456+00:00. Cursors are stored as UTC `Z` instants. */
        fun parseTs(s: String): Instant? = runCatching { OffsetDateTime.parse(s).toInstant() }
            .recoverCatching { Instant.parse(s) }
            .recoverCatching { OffsetDateTime.parse(s.replace(' ', 'T')).toInstant() }
            .getOrNull()
    }
}
