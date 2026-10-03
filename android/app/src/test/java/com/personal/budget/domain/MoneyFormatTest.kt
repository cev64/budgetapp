package com.personal.budget.domain

import com.personal.budget.domain.usecase.Money
import com.personal.budget.domain.usecase.Num
import com.personal.budget.domain.usecase.Percent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyFormatTest {
    /** Every test vector from DOMAIN_RULES §8. */
    @Test
    fun domainRules8_vectors() {
        val vectors = listOf(
            0.0 to "$0",
            12.0 to "$12",
            12.5 to "$12.50",
            999.994 to "$999.99",
            999.995 to "$1,000",
            1000.0 to "$1,000",
            1000.5 to "$1,001",
            22560.0 to "$22,560",
            2886.6667 to "$2,887",
            -640.25 to "\u2212$640.25",
            -1022.5 to "\u2212$1,023",
            -0.004 to "$0",
            1234.56 to "$1,235",
            -1500.4 to "\u2212$1,500",
            69.6667 to "$69.67",
            999.99 to "$999.99",
        )
        vectors.forEach { (v, expected) -> assertEquals("format($v)", expected, Money.format(v)) }
    }

    @Test
    fun wholeNumbers_haveNoCents() {
        assertEquals("$1,234", Money.format(1234.0))
        assertEquals("$0", Money.format(0.0))
        assertEquals("$1,000,000", Money.format(1_000_000.0))
        assertEquals("$950", Money.format(950.0))
    }

    @Test
    fun fractions_showTwoDecimals() {
        assertEquals("$1,059", Money.format(1059.33))
        assertEquals("$0.50", Money.format(0.5))
        assertEquals("$4.62", Money.format(1.76 + 2.68 + 0.18))
    }

    @Test
    fun negatives_useTrueMinus() {
        assertEquals("−$153", Money.format(-153.0))
        assertEquals("−$640.25", Money.format(-640.25))
        assertEquals("$0", Money.format(-0.001))
    }

    @Test
    fun rounding_isForDisplayOnly() {
        assertEquals("$1,235", Money.format(1234.999))
        assertEquals("$1.01", Money.format(1.005))
        assertEquals("$0.30", Money.format(0.1 + 0.2))
    }

    @Test
    fun signedAndCompact() {
        assertEquals("+$12", Money.formatSigned(12.0))
        assertEquals("−$4.50", Money.formatSigned(-4.5))
        assertEquals("$0", Money.formatSigned(0.0))
        assertEquals("$1.2k", Money.formatCompact(1234.0))
        assertEquals("$12k", Money.formatCompact(12345.0))
        assertEquals("$950", Money.formatCompact(950.0))
    }

    @Test
    fun percent_oneDecimal() {
        assertEquals("43.7%", Percent.format(0.437))
        assertEquals("35.0%", Percent.format(0.35))
        assertEquals("—", Percent.format(null))
    }

    @Test
    fun parse_acceptsCommonInput() {
        assertEquals(1234.5, Money.parse("1,234.50")!!, 0.0)
        assertEquals(-12.0, Money.parse("−12")!!, 0.0)
        assertEquals(5.0, Money.parse("$5")!!, 0.0)
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertEquals("1234.5", Money.formatInput(1234.5))
        assertEquals("2886.6667", Money.formatInput(2886.6667))
        assertEquals("1234", Money.formatInput(1234.0))
    }

    @Test
    fun plainNumbers() {
        assertEquals("776", Num.format(776.0))
        assertEquals("1,250.5", Num.format(1250.5))
        assertEquals("", Num.format(null))
    }
}
