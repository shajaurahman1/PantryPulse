package com.pantrypulse.app

import com.pantrypulse.app.domain.DateTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DateTextParserTest {
    private val today = LocalDate.of(2026, 11, 13)
    private fun best(text: String) = DateTextParser.best(text, today)

    @Test fun dayFirstNumeric() = assertEquals(LocalDate.of(2026, 8, 12), best("EXP 12/08/2026"))
    @Test fun twoDigitYear() = assertEquals(LocalDate.of(2027, 3, 5), best("USE BY 05.03.27"))
    @Test fun isoDate() = assertEquals(LocalDate.of(2027, 1, 30), best("BEST BEFORE 2027-01-30"))
    @Test fun monthNameDayFirst() = assertEquals(LocalDate.of(2026, 12, 4), best("BB 04 DEC 2026"))
    @Test fun compactMonthName() = assertEquals(LocalDate.of(2027, 2, 14), best("EXP14FEB27"))
    @Test fun monthNameFirst() = assertEquals(LocalDate.of(2027, 4, 9), best("Best before Apr 9, 2027"))
    @Test fun monthYearOnlyMeansEndOfMonth() = assertEquals(LocalDate.of(2027, 8, 31), best("EXP 08/27"))
    @Test fun textMonthYear() = assertEquals(LocalDate.of(2028, 1, 31), best("BEST BEFORE END JAN 2028"))
    @Test fun usStyleWhenDayFirstImpossible() = assertEquals(LocalDate.of(2027, 3, 25), best("EXP 03/25/2027"))

    @Test fun prefersExpiryOverManufactureDate() =
        assertEquals(LocalDate.of(2027, 5, 1), best("MFG 01/05/2026\nEXP 01/05/2027"))

    @Test fun prefersExpiryEvenWhenListedFirst() =
        assertEquals(LocalDate.of(2026, 12, 20), best("USE BY 20/12/2026 PKD 10/12/2026"))

    @Test fun fixesOcrLetterO() = assertEquals(LocalDate.of(2026, 10, 10), best("EXP 1O/10/2026"))

    @Test fun ignoresPricesAndWeights() = assertNull(best("NET WT 500 G  PRICE 2.99"))

    @Test fun rejectsImpossibleDates() = assertNull(best("EXP 32/13/2026"))

    @Test fun returnsSeveralCandidates() {
        val all = DateTextParser.parse("PKD 01/11/2026 EXP 01/02/2027", today)
        assertEquals(2, all.size)
        assertEquals(LocalDate.of(2027, 2, 1), all.first().date)
    }
}
