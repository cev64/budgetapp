package com.personal.budget.domain

import com.personal.budget.domain.model.MonthKey
import com.personal.budget.domain.usecase.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DOMAIN_RULES §4b test vectors on docs/fixtures/sample-backup.json. */
class LeftoverVsPlanTest {
    private val book = Fixtures.book()
    private val settings = Fixtures.backup.settings!!.toDomain()
    private val eps = 1e-9

    @Test
    fun year2025_isAheadBy261_75() {
        val y = book.yearSummary(2025, settings)!!
        assertEquals(2220.0, y.expected.totals.leftover, eps)
        assertEquals(2481.75, y.actual.totals.leftover, eps)
        assertEquals(261.75, y.leftoverVsPlan, eps)
        assertEquals(261.75, book.leftoverVsPlan(2025, settings)!!, eps)
        assertEquals(2481.75 / 2220.0, y.leftoverProgress!!, eps)
    }

    @Test
    fun closedMonths_2025() {
        assertEquals(319.50, book.monthVsPlan(MonthKey(2025, 11)), eps)
        assertEquals(-57.75, book.monthVsPlan(MonthKey(2025, 12)), eps)
        assertEquals(1110.0 + 319.50, book.projectedLeftover(MonthKey(2025, 11)), eps)
        val points = book.yearSummary(2025, settings)!!.months
        assertEquals(listOf(319.50, -57.75), points.map { it.vsPlan })
    }

    @Test
    fun monthsSumExactlyToTheYear() {
        for (year in book.years()) {
            val y = book.yearSummary(year, settings)!!
            val sum = y.months.sumOf { book.monthVsPlan(it.key) }
            assertEquals("year $year", y.leftoverVsPlan, sum, 1e-9)
            assertEquals("year $year", y.leftoverVsPlan, y.months.sumOf { it.vsPlan }, 1e-9)
        }
    }

    @Test
    fun year2026_onPlan_withJanuaryOpen() {
        val y = book.yearSummary(2026, settings)!!
        assertFalse(y.months.single().closed)
        assertEquals(0.0, book.monthVsPlan(MonthKey(2026, 1)), eps)
        assertEquals(0.0, y.leftoverVsPlan, eps)
        assertNull(book.leftoverVsPlan(2030, settings))
    }

    @Test
    fun signedDisplay_usesSection8Magnitude() {
        assertEquals("+$1,380", Money.formatSigned(1380.0))
        assertEquals("−$45", Money.formatSigned(-45.0))
        assertEquals("$0", Money.formatSigned(0.004))
        assertEquals("+$261.75", Money.formatSigned(261.75))
        assertTrue(Money.formatSigned(-1022.5) == "−$1,023")
    }
}
