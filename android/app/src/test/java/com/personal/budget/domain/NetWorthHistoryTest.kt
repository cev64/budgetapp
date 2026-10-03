package com.personal.budget.domain

import com.personal.budget.domain.model.AccountGroup
import com.personal.budget.domain.model.NetWorthSnapshot
import com.personal.budget.domain.model.SnapshotAccount
import com.personal.budget.domain.usecase.HistoryRange
import com.personal.budget.domain.usecase.NetWorthHistoryMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetWorthHistoryTest {
    private fun snap(day: String, nw: Double, liquid: Double = nw / 2, acct: Double? = null) = NetWorthSnapshot(
        takenOn = day, netWorth = nw, superLiquid = liquid, reconciliations = 0.0,
        accounts = acct?.let { listOf(SnapshotAccount("a1", "Checking", AccountGroup.CASH, true, it)) }.orEmpty(),
    )

    // Unsorted on purpose.
    private val history = listOf(
        snap("2026-10-02", 22560.0, acct = 5780.0),
        snap("2026-01-01", 10000.0),
        snap("2026-07-04", 18000.0, acct = 5000.0),
        snap("2026-09-02", 21000.0, acct = 5500.0),
        snap("2026-09-01", 20500.0),
        snap("2025-10-01", 8000.0),
    )

    @Test
    fun oneMonth_usesSnapshotOnOrBeforeCutoff() {
        // latest 2026-10-02 − 30 days = 2026-09-02 → that exact day qualifies ("on or before").
        val c = NetWorthHistoryMath.changeOver(history, HistoryRange.M1)!!
        assertEquals("2026-09-02", c.base.takenOn)
        assertEquals(1560.0, c.amount, 1e-9)
        assertFalse(c.sinceFallback)
    }

    @Test
    fun threeMonths_picksLatestOldEnough() {
        // cutoff 2026-07-03 → newest snapshot on or before it is 2026-01-01.
        val c = NetWorthHistoryMath.changeOver(history, HistoryRange.M3)!!
        assertEquals("2026-01-01", c.base.takenOn)
        assertEquals(12560.0, c.amount, 1e-9)
    }

    @Test
    fun oneYear_andAll() {
        // cutoff 2025-10-02 → 2025-10-01 qualifies.
        assertEquals("2025-10-01", NetWorthHistoryMath.changeOver(history, HistoryRange.Y1)!!.base.takenOn)
        val all = NetWorthHistoryMath.changeOver(history, HistoryRange.ALL)!!
        assertEquals("2025-10-01", all.base.takenOn)
        assertEquals(14560.0, all.amount, 1e-9)
        assertFalse(all.sinceFallback)
    }

    @Test
    fun notOldEnough_fallsBackToOldest_since() {
        val short = listOf(snap("2026-09-20", 100.0), snap("2026-10-02", 250.0))
        val c = NetWorthHistoryMath.changeOver(short, HistoryRange.M1)!!
        assertTrue(c.sinceFallback)
        assertEquals("2026-09-20", c.base.takenOn)
        assertEquals(150.0, c.amount, 1e-9)
    }

    @Test
    fun emptyAndSingle() {
        assertNull(NetWorthHistoryMath.changeOver(emptyList(), HistoryRange.M1))
        val one = NetWorthHistoryMath.changeOver(listOf(snap("2026-10-02", 5.0)), HistoryRange.M1)!!
        assertEquals(0.0, one.amount, 0.0)
        assertTrue(one.sinceFallback)
        assertNull(NetWorthHistoryMath.seriesChange(NetWorthHistoryMath.series(listOf(snap("2026-10-02", 5.0)), HistoryRange.ALL)))
    }

    @Test
    fun otherValues_superLiquid() {
        val c = NetWorthHistoryMath.changeOver(history, HistoryRange.M1) { it.superLiquid }!!
        assertEquals(22560.0 / 2 - 21000.0 / 2, c.amount, 1e-9)
    }

    @Test
    fun series_startsAtBase_soEndpointsMatchChange() {
        val pts = NetWorthHistoryMath.series(history, HistoryRange.M1)
        assertEquals(listOf("2026-09-02", "2026-10-02"), pts.map { it.date.toString() })
        assertEquals(NetWorthHistoryMath.changeOver(history, HistoryRange.M1)!!.amount, NetWorthHistoryMath.seriesChange(pts)!!, 1e-9)
        val m3 = NetWorthHistoryMath.series(history, HistoryRange.M3)
        assertEquals(listOf("2026-01-01", "2026-07-04", "2026-09-01", "2026-09-02", "2026-10-02"), m3.map { it.date.toString() })
        assertEquals(6, NetWorthHistoryMath.series(history, HistoryRange.ALL).size)
    }

    @Test
    fun perAccountSeries_onlySnapshotsContainingTheAccount() {
        val s = NetWorthHistoryMath.accountSeries(history, "a1")
        assertEquals(listOf("2026-07-04", "2026-09-02", "2026-10-02"), s.map { it.date.toString() })
        assertEquals(listOf(5000.0, 5500.0, 5780.0), s.map { it.value })
        assertTrue(NetWorthHistoryMath.accountSeries(history, "missing").isEmpty())
    }
}
