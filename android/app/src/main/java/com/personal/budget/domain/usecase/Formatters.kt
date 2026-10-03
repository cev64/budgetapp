package com.personal.budget.domain.usecase

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs

/** docs/DOMAIN_RULES.md §8 display rules. Storage is never rounded; only display is. */
object Money {
    const val MINUS = '−'

    /**
     * DOMAIN_RULES §8: |value| ≥ 1,000 → whole dollars (half away from zero); below that `$12`
     * when whole, `$12.34` otherwise. Negative as `−$153` (U+2212); zero is `$0`.
     */
    fun format(value: Double, symbol: String = "$"): String {
        var rounded = toCents(value)
        if (rounded.abs() >= THOUSAND) rounded = rounded.setScale(0, RoundingMode.HALF_UP)
        if (rounded.signum() == 0) rounded = BigDecimal.ZERO
        val negative = rounded.signum() < 0
        val abs = rounded.abs()
        val whole = abs.setScale(0, RoundingMode.DOWN)
        val isWhole = abs.compareTo(whole) == 0
        val intPart = group(whole.toBigInteger().toString())
        val body = if (isWhole) intPart else intPart + "." + abs.setScale(2, RoundingMode.HALF_UP).toPlainString().substringAfter('.')
        return (if (negative) MINUS.toString() else "") + symbol + body
    }

    private val THOUSAND = BigDecimal(1000)

    /** Blank for null (manual category with nothing typed). */
    fun formatOrBlank(value: Double?, blank: String = ""): String = value?.let { format(it) } ?: blank

    /** With an explicit sign for differences: `+$12`, `−$4.50`, `$0`. */
    fun formatSigned(value: Double): String {
        val cents = toCents(value)
        return when {
            cents.signum() > 0 -> "+" + format(value)
            else -> format(value)
        }
    }

    /** Short form for chart axes: `$950`, `$1.2k`, `$12k`. */
    fun formatCompact(value: Double): String {
        val a = abs(value)
        val sign = if (value < 0) MINUS.toString() else ""
        return when {
            a >= 10_000 -> sign + "$" + (a / 1000).toLong() + "k"
            a >= 1_000 -> sign + "$" + String.format(Locale.US, "%.1f", a / 1000).removeSuffix(".0") + "k"
            else -> sign + "$" + a.toLong()
        }
    }

    /**
     * Editable fields show the exact stored value (never the rounded display): `1234.5` →
     * "1234.5", `2886.6667` → "2886.6667", whole → "1234".
     */
    fun formatInput(value: Double?): String {
        if (value == null) return ""
        val exact = BigDecimal(value.toString()).stripTrailingZeros()
        return if (exact.scale() <= 0) exact.setScale(0).toPlainString() else exact.toPlainString()
    }

    /** Parses user input ("1,234.50", "−12", "$5"). Returns null for blank/invalid. */
    fun parse(input: String): Double? {
        val cleaned = input.trim()
            .replace(MINUS, '-')
            .replace("$", "")
            .replace(",", "")
            .replace(" ", "")
        if (cleaned.isEmpty() || cleaned == "-" || cleaned == ".") return null
        return cleaned.toBigDecimalOrNull()?.toDouble()
    }

    internal fun toCents(value: Double): BigDecimal =
        BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP).let {
            if (it.signum() == 0) it.abs() else it
        }

    private fun group(digits: String): String {
        val sb = StringBuilder()
        val len = digits.length
        for (i in digits.indices) {
            if (i > 0 && (len - i) % 3 == 0) sb.append(',')
            sb.append(digits[i])
        }
        return sb.toString()
    }
}

object Percent {
    /** One decimal: 0.437 → "43.7%". */
    fun format(fraction: Double?): String =
        if (fraction == null || fraction.isNaN() || fraction.isInfinite()) "—"
        else String.format(Locale.US, "%.1f%%", fraction * 100).replace('-', Money.MINUS)
}

/** Plain numbers (no currency), up to one decimal, grouped. */
object Num {
    fun format(value: Double?): String {
        if (value == null) return ""
        val bd = BigDecimal(value.toString()).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros()
        val s = if (bd.scale() < 0) bd.setScale(0).toPlainString() else bd.toPlainString()
        val neg = s.startsWith("-")
        val digits = s.removePrefix("-")
        val intPart = digits.substringBefore('.')
        val frac = digits.substringAfter('.', "")
        val grouped = StringBuilder().apply {
            for (i in intPart.indices) {
                if (i > 0 && (intPart.length - i) % 3 == 0) append(',')
                append(intPart[i])
            }
        }.toString()
        return (if (neg) Money.MINUS.toString() else "") + grouped + (if (frac.isNotEmpty()) ".$frac" else "")
    }
}
