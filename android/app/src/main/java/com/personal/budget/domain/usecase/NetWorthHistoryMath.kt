package com.personal.budget.domain.usecase

import com.personal.budget.domain.model.NetWorthSnapshot
import java.time.LocalDate

/** History ranges (DOMAIN_RULES §5b): 1M = 30 days, 3M = 91, 6M = 182, 1Y = 365, All. */
enum class HistoryRange(val label: String, val days: Long?) {
    M1("1M", 30), M3("3M", 91), M6("6M", 182), Y1("1Y", 365), ALL("All", null),
}

data class HistoryPoint(val date: LocalDate, val value: Double)

/**
 * A change between the latest snapshot and a base snapshot.
 * [sinceFallback] = no snapshot was old enough, so the oldest one was used ("since <date>").
 */
data class HistoryChange(
    val amount: Double,
    val latest: NetWorthSnapshot,
    val base: NetWorthSnapshot,
    val sinceFallback: Boolean,
)

object NetWorthHistoryMath {
    /** Snapshots sorted by day (ties impossible: one per day). */
    fun sorted(snapshots: List<NetWorthSnapshot>): List<NetWorthSnapshot> = snapshots.sortedBy { it.takenOn }

    /**
     * changeOver(period) = latest.v − (snapshot on or before latest.taken_on − period).v; if no
     * snapshot is that old, the oldest snapshot is used (labelled "since <date>"). For [HistoryRange.ALL]
     * the base is the first snapshot. Null with no snapshots.
     */
    fun changeOver(
        snapshots: List<NetWorthSnapshot>,
        range: HistoryRange,
        value: (NetWorthSnapshot) -> Double = { it.netWorth },
    ): HistoryChange? {
        val s = sorted(snapshots)
        val latest = s.lastOrNull() ?: return null
        val oldest = s.first()
        val days = range.days ?: return HistoryChange(value(latest) - value(oldest), latest, oldest, sinceFallback = false)
        val cutoff = latest.date.minusDays(days)
        val base = s.lastOrNull { !it.date.isAfter(cutoff) }
        return if (base != null) {
            HistoryChange(value(latest) - value(base), latest, base, sinceFallback = false)
        } else {
            HistoryChange(value(latest) - value(oldest), latest, oldest, sinceFallback = true)
        }
    }

    /**
     * Points for a chart of [range]: every snapshot after the cutoff, preceded by the base snapshot
     * (on or before the cutoff) so the line's first and last points match [changeOver].
     */
    fun series(
        snapshots: List<NetWorthSnapshot>,
        range: HistoryRange,
        value: (NetWorthSnapshot) -> Double = { it.netWorth },
    ): List<HistoryPoint> {
        val s = sorted(snapshots)
        val latest = s.lastOrNull() ?: return emptyList()
        val days = range.days ?: return s.map { HistoryPoint(it.date, value(it)) }
        val cutoff = latest.date.minusDays(days)
        val base = s.lastOrNull { !it.date.isAfter(cutoff) }
        val inRange = s.filter { it.date.isAfter(cutoff) }
        return (listOfNotNull(base) + inRange).map { HistoryPoint(it.date, value(it)) }
    }

    /** perAccountSeries(id) = [(taken_on, accounts[id].balance)] for snapshots containing the account. */
    fun accountSeries(snapshots: List<NetWorthSnapshot>, accountId: String): List<HistoryPoint> =
        sorted(snapshots).mapNotNull { snap ->
            snap.accounts.firstOrNull { it.id == accountId }?.let { HistoryPoint(snap.date, it.balance) }
        }

    /** Change between the first and last point of a series, or null with fewer than 2 points. */
    fun seriesChange(points: List<HistoryPoint>): Double? =
        if (points.size < 2) null else points.last().value - points.first().value
}
