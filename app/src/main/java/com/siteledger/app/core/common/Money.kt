package com.siteledger.app.core.common

import java.text.NumberFormat
import java.util.Locale

/**
 * Money rules for V1 (blueprint section 02):
 * - KES amounts are whole shillings stored in Long. Float/Double are never used for money.
 * - Measured quantities use a scaled integer: quantityMilli = quantity x 1000.
 */
object Money {

    private const val QUANTITY_SCALE = 1000L
    private const val MAX_QUANTITY_WHOLE = 1_000_000_000L

    private val formatter = ThreadLocal.withInitial {
        NumberFormat.getIntegerInstance(Locale.US).apply {
            isGroupingUsed = true
            maximumFractionDigits = 0
        }
    }

    fun formatKes(amountKes: Long): String = "KSh " + grouped(amountKes)

    fun grouped(amountKes: Long): String = formatter.get()!!.format(amountKes)

    /** Parses user input such as "12,500" or "12500" into whole shillings. Null when invalid. */
    fun parseKes(text: String): Long? {
        val cleaned = text.trim().replace(",", "").replace(" ", "")
        if (cleaned.isEmpty()) return null
        return cleaned.toLongOrNull()?.takeIf { it >= 0 }
    }

    /** Parses a decimal quantity ("3.5", "0.25") into scaled milli-units. Null when invalid. */
    fun parseQuantityMilli(text: String): Long? {
        val cleaned = text.trim().replace(",", "")
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split('.')
        if (parts.size > 2) return null
        val wholeText = parts[0].ifEmpty { "0" }
        val whole = wholeText.toLongOrNull() ?: return null
        if (whole < 0 || whole > MAX_QUANTITY_WHOLE) return null
        val fracText = if (parts.size == 2) parts[1] else ""
        if (fracText.length > 3 || fracText.any { !it.isDigit() }) return null
        val frac = if (fracText.isEmpty()) 0L else fracText.padEnd(3, '0').toLong()
        return whole * QUANTITY_SCALE + frac
    }

    fun formatQuantity(quantityMilli: Long): String {
        val whole = quantityMilli / QUANTITY_SCALE
        val frac = quantityMilli % QUANTITY_SCALE
        if (frac == 0L) return whole.toString()
        val fracText = frac.toString().padStart(3, '0').trimEnd('0')
        return "$whole.$fracText"
    }

    /** amount = quantity x unit price, rounded to the nearest whole shilling. */
    fun amountFromQuantity(quantityMilli: Long, unitPriceKes: Long): Long {
        val product = quantityMilli * unitPriceKes
        return (product + QUANTITY_SCALE / 2) / QUANTITY_SCALE
    }
}
