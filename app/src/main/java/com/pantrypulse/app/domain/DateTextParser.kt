package com.pantrypulse.app.domain

import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth

/**
 * Finds expiry dates in text read by OCR from a package label.
 *
 * Labels print dates in many shapes ("EXP 12/08/2026", "BEST BEFORE 12 AUG 26",
 * "USE BY 2026-08-12", "BB 08/26"). Every date-like token is collected and scored:
 * tokens near expiry words ("EXP", "USE BY", "BEST BEFORE") score highest, tokens near
 * manufacturing words ("MFG", "PKD", "PACKED") score lowest, and later dates win ties
 * because the expiry date is normally the latest date printed on a pack.
 */
object DateTextParser {

    data class Candidate(val date: LocalDate, val source: String, val score: Int)

    private val months = mapOf(
        "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4, "MAY" to 5, "JUN" to 6,
        "JUL" to 7, "AUG" to 8, "SEP" to 9, "SEPT" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12,
    )
    private const val MONTH = "(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEPT|SEP|OCT|NOV|DEC)[A-Z]*"
    private const val SEP = "[./\\- ]"

    private val expiryWords = Regex("(EXP|EXPIRY|EXPIRES|USE BY|USE-BY|USEBY|BEST BEFORE|BEST BY|BB|BBE|BEST BEF|CONSUME BY|SELL BY|BEST)")
    private val makeWords = Regex("(MFG|MFD|MANUFACTURED|PKD|PACKED|PACKAGED|PROD|PRODUCED|MFG\\.? DATE|DOM)")

    // Ordered from most to least specific. Each pattern yields a date or null.
    private val numericYmd = Regex("(?<!\\d)(20\\d{2})$SEP(\\d{1,2})$SEP(\\d{1,2})(?!\\d)")
    private val numericDmy = Regex("(?<!\\d)(\\d{1,2})$SEP(\\d{1,2})$SEP(\\d{4}|\\d{2})(?!\\d)")
    private val textDmy = Regex("(?<!\\d)(\\d{1,2})\\s?$SEP?\\s?$MONTH\\s?$SEP?\\s?(\\d{4}|\\d{2})(?!\\d)")
    private val textMdy = Regex("\\b$MONTH\\s?$SEP?\\s?(\\d{1,2}),?\\s?$SEP?\\s?(\\d{4})\\b")
    private val textMy = Regex("\\b$MONTH\\s?$SEP?\\s?(\\d{4}|\\d{2})\\b")
    // Month/year only ("08/26") — no space separator, which would match quantities.
    private val numericMy = Regex("(?<!\\d)(\\d{1,2})[./\\-](20\\d{2}|\\d{2})(?![\\d./\\-])")
    private val compactDmy = Regex("(?<!\\d)(\\d{2})(\\d{2})(20\\d{2})(?!\\d)")

    fun parse(text: String, today: LocalDate = LocalDate.now()): List<Candidate> {
        val upper = normalise(text)
        val found = mutableListOf<Candidate>()
        val consumed = mutableListOf<IntRange>()

        fun add(match: MatchResult, date: LocalDate?) {
            if (date == null) return
            if (consumed.any { it.first <= match.range.last && match.range.first <= it.last }) return
            if (date.isBefore(today.minusYears(3)) || date.isAfter(today.plusYears(15))) return
            consumed += match.range
            found += Candidate(date, match.value.trim(), score(upper, match.range, date, today))
        }

        numericYmd.findAll(upper).forEach { m ->
            val (y, mo, d) = m.destructured
            add(m, safeDate(y.toInt(), mo.toInt(), d.toInt()))
        }
        textDmy.findAll(upper).forEach { m ->
            val (d, mon, y) = m.destructured
            add(m, safeDate(year(y), months.getValue(mon.take(3)), d.toInt()))
        }
        textMdy.findAll(upper).forEach { m ->
            val (mon, d, y) = m.destructured
            add(m, safeDate(y.toInt(), months.getValue(mon.take(3)), d.toInt()))
        }
        numericDmy.findAll(upper).forEach { m ->
            val (a, b, y) = m.destructured
            val first = a.toInt()
            val second = b.toInt()
            // Day-first is the default (used in most countries); switch to month-first
            // only when the numbers make day-first impossible.
            val date = if (second > 12 && first <= 12) safeDate(year(y), first, second)
            else safeDate(year(y), second, first)
            add(m, date)
        }
        compactDmy.findAll(upper).forEach { m ->
            val (d, mo, y) = m.destructured
            add(m, safeDate(y.toInt(), mo.toInt(), d.toInt()))
        }
        textMy.findAll(upper).forEach { m ->
            val (mon, y) = m.destructured
            add(m, endOfMonth(year(y), months.getValue(mon.take(3))))
        }
        numericMy.findAll(upper).forEach { m ->
            val (mo, y) = m.destructured
            add(m, endOfMonth(year(y), mo.toInt()))
        }

        return found
            .sortedWith(compareByDescending<Candidate> { it.score }.thenByDescending { it.date })
            .distinctBy { it.date }
    }

    fun best(text: String, today: LocalDate = LocalDate.now()): LocalDate? = parse(text, today).firstOrNull()?.date

    private fun normalise(text: String): String = text.uppercase()
        // Common OCR confusions inside numbers: O/Q for 0 and I/L/| for 1 when they
        // sit between a digit and a digit or date separator.
        .replace(Regex("(?<=\\d)[OQ](?=[\\d./\\-])|(?<=[\\d./\\-])[OQ](?=\\d)"), "0")
        .replace(Regex("(?<=\\d)[IL|](?=[\\d./\\-])|(?<=[\\d./\\-])[IL|](?=\\d)"), "1")
        .replace(Regex("[ \\t]+"), " ")

    private fun score(text: String, range: IntRange, date: LocalDate, today: LocalDate): Int {
        val windowStart = (range.first - 28).coerceAtLeast(0)
        val before = text.substring(windowStart, range.first)
        // Only look at the text on the same line (or just above) for context words.
        val context = before.substringAfterLast("\n\n")
        var s = 0
        val lastExpiry = expiryWords.findAll(context).lastOrNull()?.range?.last ?: -1
        val lastMake = makeWords.findAll(context).lastOrNull()?.range?.last ?: -1
        if (lastExpiry > lastMake) s += 50
        if (lastMake > lastExpiry) s -= 40
        if (!date.isBefore(today)) s += 10
        return s
    }

    private fun year(y: String): Int = if (y.length == 2) 2000 + y.toInt() else y.toInt()

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? = try {
        LocalDate.of(y, m, d)
    } catch (_: DateTimeException) {
        null
    }

    private fun endOfMonth(y: Int, m: Int): LocalDate? = try {
        YearMonth.of(y, m).atEndOfMonth()
    } catch (_: DateTimeException) {
        null
    }
}
