package dk.binumre.startlist

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class NameFormat { GIVEN_FAMILY, FAMILY_GIVEN }

/** Én løber i startlisten. [start] er epoch-millisekunder (null = fri start). */
data class StartEntry(
    /** OFeeds deltager-id (bruges til at flette ændringer ind); tom for fil/URL. */
    val id: String = "",
    val given: String = "",
    val family: String = "",
    /** Fuldt navn når fornavn/efternavn ikke findes hver for sig (CSV). */
    val name: String = "",
    val club: String = "",
    val cls: String = "",
    val bib: String = "",
    val start: Long? = null,
    val card: Long? = null,
    val course: String = "",
    val startName: String = "",
    /** Stafet: holdets navn og turens nummer hver for sig (tom for individuelle løb). */
    val teamName: String = "",
    val leg: String = "",
    /** Banens længde i meter (IOF XML "Course/Length"). */
    val courseLength: Int? = null,
    /** Status fra OFeed (fx "Inactive", "Active", "OK", "DidNotStart") – tom for fil/URL. */
    val status: String = "",
    /** Fravalgt i brugerfladen: løberen får ikke noget startnummer. */
    val excluded: Boolean = false,
    /** Flerdagesløb i én fil (IOF XML "raceNumber"): hvilken etape linjen gælder; null = alle. */
    val race: Int? = null,
) {
    fun displayName(format: NameFormat): String =
        if (given.isEmpty() && family.isEmpty()) name
        else when (format) {
            NameFormat.GIVEN_FAMILY -> listOf(given, family)
            NameFormat.FAMILY_GIVEN -> listOf(family, given)
        }.filter { it.isNotEmpty() }.joinToString(if (format == NameFormat.FAMILY_GIVEN) ", " else " ")
}

data class StartList(
    val event: String,
    /** Hvor listen kom fra (filnavn eller URL). */
    val source: String,
    val loadedAt: Long,
    val entries: List<StartEntry>,
    /** OFeed: tidspunkt til næste "hent ændringer siden" (null = hent alt). */
    val cursor: String? = null,
    /** Løbets dato fra filen (IOF XML "Event/StartTime/Date"), ISO yyyy-MM-dd. */
    val date: String? = null,
) {
    /** Løbets dato: fra filen, ellers dagen for den tidligste starttid. */
    val eventDate: LocalDate? by lazy {
        date?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
            ?: entries.mapNotNull { it.start }.minOrNull()?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
    }

    val classes: List<String> by lazy {
        entries.map { it.cls }.filter { it.isNotEmpty() }.distinct().sortedWith(NaturalOrder)
    }
}

/** Sorterer "H10, H12, H21" og "1, 2, 10" som et menneske ville. */
object NaturalOrder : Comparator<String> {
    private val chunk = Regex("\\d+|\\D+")
    override fun compare(a: String, b: String): Int {
        val x = chunk.findAll(a.lowercase()).map { it.value }.toList()
        val y = chunk.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]
            val q = y[i]
            val c = if (p[0].isDigit() && q[0].isDigit()) {
                p.trimStart('0').length.compareTo(q.trimStart('0').length).takeIf { it != 0 }
                    ?: p.trimStart('0').compareTo(q.trimStart('0'))
            } else p.compareTo(q)
            if (c != 0) return c
        }
        return x.size.compareTo(y.size)
    }
}
