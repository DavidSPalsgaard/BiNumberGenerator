package dk.binumre.model

import dk.binumre.startlist.NaturalOrder
import dk.binumre.startlist.StartEntry
import dk.binumre.startlist.StartList

/** Løberens oplysninger for ét løb/én etape (linjen i bunden af startnummeret). */
data class BibStage(
    val start: Long?,
    val cls: String,
    val course: String,
    val courseLength: Int?,
    val startName: String,
)

/** Ét startnummer. [stages] har ét element, undtagen for flerdagesløb (null = løber ikke den etape). */
data class Bib(
    val number: String,
    val given: String,
    val family: String,
    val name: String,
    val club: String,
    val card: Long?,
    val cls: String,
    /** Stafet: holdnavn og tur. */
    val teamName: String = "",
    val leg: String = "",
    val stages: List<BibStage?>,
) {
    fun displayName(format: dk.binumre.startlist.NameFormat): String =
        StartEntry(given = given, family = family, name = name).displayName(format)

    /** Entydig nøgle: nummeret (og turen ved stafet). Bruges til at huske hvad der er udskrevet. */
    val key: String get() = if (leg.isEmpty()) number else "$number/$leg"
}

/** Resultatet af at lave startnumre: numrene og hvor mange (ikke fravalgte) løbere der blev sprunget over uden startnummer. */
data class BibSet(val bibs: List<Bib>, val withoutNumber: Int)

object BibBuilder {

    /**
     * Laver startnumre fra én startliste (enkeltløb, stafet) eller én pr. etape (flerdagesløb; null = etapen
     * mangler endnu). Ved flerdagesløb med [BibProject.sharedStartList] bruges den første liste til alle etaper –
     * har den "raceNumber" pr. start (IOF XML), tages hver etapes linjer derfra.
     */
    fun build(project: BibProject, lists: List<StartList?>, classes: Set<String> = emptySet()): BibSet {
        require(lists.any { it != null }) { "Ingen startliste" }
        fun wanted(e: StartEntry) = classes.isEmpty() || e.cls in classes
        fun stageEntries(list: StartList?, stage: Int) =
            list?.entries.orEmpty().filter { wanted(it) && (it.race == null || it.race == stage + 1) }
        val all = when (project.type) {
            BibType.SINGLE -> single(stageEntries(lists[0], 0))
            BibType.RELAY -> relay(stageEntries(lists[0], 0))
            BibType.MULTIDAY -> {
                val shared = project.sharedStartList
                val count = maxOf(project.stageCount, if (shared) 1 else lists.size)
                multiday((0 until count).map { i -> stageEntries(if (shared) lists[0] else lists.getOrNull(i), i) })
            }
        }
        val numbered = all.filter { it.number.isNotEmpty() }
        return BibSet(sort(numbered, project.sortBy), all.size - numbered.size)
    }

    private fun stage(e: StartEntry) = BibStage(e.start, e.cls, e.course, e.courseLength, e.startName)

    private fun bib(e: StartEntry, number: String, stages: List<BibStage?>) = Bib(
        number = number, given = e.given, family = e.family, name = e.name, club = e.club,
        card = e.card, cls = e.cls, teamName = e.teamName, leg = e.leg, stages = stages,
    )

    /** En løber med flere SI-brikker står flere gange i listen – kun ét startnummer pr. løber. */
    private fun single(entries: List<StartEntry>): List<Bib> =
        included(entries) { listOf(it.bib, personKey(it), it.cls) }.map { bib(it, it.bib, listOf(stage(it))) }

    private fun relay(entries: List<StartEntry>): List<Bib> =
        included(entries) { listOf(it.bib, it.teamName, it.leg, personKey(it), it.cls) }
            .map { bib(it, teamNumber(it.bib, it.leg), listOf(stage(it))) }

    /** Én linje pr. løber (første forekomst); løbere hvor en af linjerne er fravalgt, udelades. */
    private fun included(entries: List<StartEntry>, key: (StartEntry) -> Any): List<StartEntry> =
        entries.groupBy(key).values.filter { g -> g.none { it.excluded } }.map { it.first() }

    /** Holdets nummer: "12.1", "12-1" og "12/1" bliver til "12" når tallet efter tegnet er turen. */
    fun teamNumber(bib: String, leg: String): String {
        val m = Regex("^(.+?)[.\\-/](\\d+)$").find(bib.trim()) ?: return bib.trim()
        return if (leg.isEmpty() || m.groupValues[2] == leg) m.groupValues[1] else bib.trim()
    }

    /** Samme løber på tværs af etaper: samme startnummer, ellers samme SI-brik, ellers samme navn og klub. */
    private fun multiday(lists: List<List<StartEntry>>): List<Bib> {
        class Runner(var entry: StartEntry, val stages: Array<BibStage?>, var number: String, var excluded: Boolean = false)
        val runners = mutableListOf<Runner>()
        val byBib = mutableMapOf<String, Runner>()
        val byCard = mutableMapOf<Long, Runner>()
        val byPerson = mutableMapOf<String, Runner>()
        lists.forEachIndexed { i, entries ->
            for (e in entries) {
                val r = e.bib.trim().ifEmpty { null }?.let { byBib[it] } ?: e.card?.let { byCard[it] } ?: byPerson[personKey(e)]
                    ?: Runner(e, arrayOfNulls(lists.size), "").also { runners += it }
                if (r.stages[i] == null) r.stages[i] = stage(e)
                if (r.number.isEmpty()) r.number = e.bib.trim()
                if (r.number.isNotEmpty()) byBib.putIfAbsent(r.number, r)
                if (e.excluded) r.excluded = true
                if (r.entry.card == null && e.card != null) r.entry = r.entry.copy(card = e.card)
                e.card?.let { byCard.putIfAbsent(it, r) }
                byPerson.putIfAbsent(personKey(e), r)
            }
        }
        return runners.filter { !it.excluded }.map { bib(it.entry, it.number, it.stages.toList()) }
    }

    private fun personKey(e: StartEntry) =
        listOf(e.given, e.family, e.name, e.club).joinToString("|") { it.trim().lowercase() }

    private fun sort(bibs: List<Bib>, by: SortBy): List<Bib> {
        val number = compareBy<Bib, String>(NaturalOrder) { it.number }.thenBy(NaturalOrder) { it.leg }
        return when (by) {
            SortBy.BIB -> bibs.sortedWith(number)
            SortBy.CLUB -> bibs.sortedWith(compareBy<Bib> { it.club.lowercase() }.then(number))
            SortBy.CLASS -> bibs.sortedWith(compareBy<Bib, String>(NaturalOrder) { it.cls }.then(number))
            SortBy.NAME -> bibs.sortedWith(compareBy<Bib> { (it.family + " " + it.given + it.name).lowercase() }.then(number))
        }
    }
}
