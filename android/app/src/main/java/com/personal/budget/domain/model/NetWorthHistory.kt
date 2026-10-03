package com.personal.budget.domain.model

/** One account inside a snapshot (DOMAIN_RULES §5b), as computed server-side. */
data class SnapshotAccount(
    val id: String,
    val name: String,
    val group: AccountGroup,
    val liquid: Boolean,
    val balance: Double,
)

/**
 * One row of `net_worth_snapshots`: a day's net worth (America/New_York calendar day).
 * Written by the server (nightly job / take_net_worth_snapshot RPC); clients only read it,
 * except backup import (source = "import").
 */
data class NetWorthSnapshot(
    /** yyyy-MM-dd */
    val takenOn: String,
    val netWorth: Double,
    val superLiquid: Double,
    val reconciliations: Double,
    val accounts: List<SnapshotAccount> = emptyList(),
    val source: String = "auto",
) {
    val date: java.time.LocalDate get() = java.time.LocalDate.parse(takenOn)
}
