package be.chatpate

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object Fmt {
    private val loc: Locale = Locale.forLanguageTag("fr-BE")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

    fun time(ms: Long): String =
        timeFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

    fun timeMin(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

    fun dayName(d: LocalDate): String {
        val diff = ChronoUnit.DAYS.between(LocalDate.now(ZoneId.systemDefault()), d)
        return when (diff) {
            0L -> "Aujourd'hui"
            1L -> "Demain"
            -1L -> "Hier"
            else -> DateTimeFormatter.ofPattern("EEE d MMM", loc).format(d)
        }
    }

    fun date(ms: Long): String =
        dayName(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate())

    fun dateLong(d: LocalDate): String =
        DateTimeFormatter.ofPattern("EEE d MMM yyyy", loc).format(d)

    /** "Mardi 27 octobre" */
    fun dayFull(d: LocalDate): String =
        cap(DateTimeFormatter.ofPattern("EEEE d MMMM", loc).format(d))

    /** "Octobre 2026" */
    fun monthTitle(m: YearMonth): String =
        cap(DateTimeFormatter.ofPattern("MMMM yyyy", loc).format(m))

    fun every(n: Int): String = if (n <= 1) "Tous les jours" else "Tous les $n jours"
}
