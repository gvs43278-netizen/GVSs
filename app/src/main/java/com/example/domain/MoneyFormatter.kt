package com.example.domain

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object MoneyFormatter {
    private val integerFormatter = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US))
    private val decimalFormatter = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

    private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    /**
     * Parses a user-entered Taka string (supporting both Bengali ০-৯ and English 0-9 digits)
     * into exact integer paisa (1 Taka = 100 Paisa) without floating point arithmetic.
     */
    fun parseTakaToPaisa(raw: String): Long? {
        val normalized = raw.trim()
            .replace(",", "")
            .replace("৳", "")
            .replace(" ", "")
            .map { ch ->
                when (ch) {
                    '০' -> '0'
                    '১' -> '1'
                    '২' -> '2'
                    '৩' -> '3'
                    '৪' -> '4'
                    '৫' -> '5'
                    '৬' -> '6'
                    '৭' -> '7'
                    '৮' -> '8'
                    '৯' -> '9'
                    else -> ch
                }
            }
            .joinToString("")

        if (normalized.isEmpty()) return null
        if (normalized.startsWith("-")) return null

        val parts = normalized.split(".")
        if (parts.size > 2) return null

        val wholePartStr = parts[0].ifEmpty { "0" }
        if (!wholePartStr.all { it.isDigit() }) return null
        val wholeTaka = wholePartStr.toLongOrNull() ?: return null
        if (wholeTaka > 100_000_000_000L) return null

        val paisaPart = if (parts.size == 2) {
            val frac = parts[1]
            if (frac.isEmpty()) 0L
            else if (!frac.all { it.isDigit() } || frac.length > 2) return null
            else if (frac.length == 1) (frac.toLong() * 10L)
            else frac.toLong()
        } else {
            0L
        }

        return wholeTaka * 100L + paisaPart
    }

    fun formatTaka(paisa: Long, showPlusSign: Boolean = false): String {
        val sign = when {
            paisa < 0L -> "-"
            paisa > 0L && showPlusSign -> "+"
            else -> ""
        }
        val absPaisa = abs(paisa)
        val whole = absPaisa / 100L
        val rem = absPaisa % 100L
        val numberStr = if (rem == 0L) {
            integerFormatter.format(whole)
        } else {
            decimalFormatter.format(whole + rem / 100.0)
        }
        return "${sign}৳$numberStr"
    }

    fun formatTakaPlain(paisa: Long): String {
        val sign = if (paisa < 0L) "-" else ""
        val absPaisa = abs(paisa)
        val whole = absPaisa / 100L
        val rem = absPaisa % 100L
        return if (rem == 0L) "$sign$whole" else "$sign$whole.${rem.toString().padStart(2, '0')}"
    }

    fun formatDateTime(millis: Long): String =
        if (millis <= 0L) "—" else dateTimeFormat.format(Date(millis))

    fun formatDate(millis: Long): String =
        if (millis <= 0L) "—" else dateFormat.format(Date(millis))

    fun formatIsoDateTime(millis: Long): String =
        if (millis <= 0L) "" else isoDateFormat.format(Date(millis))
}
