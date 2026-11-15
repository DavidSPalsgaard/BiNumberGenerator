package dk.binumre.desktop.ui

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dk.binumre.desktop.ImageCache
import dk.binumre.desktop.PdfWriter
import dk.binumre.desktop.ProjectFiles
import dk.binumre.layout.BibLayout
import dk.binumre.layout.GraphicEdit
import dk.binumre.model.Bib
import dk.binumre.model.BibBuilder
import dk.binumre.model.BibElement
import dk.binumre.model.BibProject
import dk.binumre.model.BibSet
import dk.binumre.model.BibStage
import dk.binumre.model.BibType
import dk.binumre.model.Graphic
import dk.binumre.model.GraphicRole
import dk.binumre.model.OFeedSource
import dk.binumre.ofeed.mergeChanges
import dk.binumre.model.Stage
import dk.binumre.ofeed.OFeedClient
import dk.binumre.ofeed.OFeedConnection
import dk.binumre.startlist.StartEntry
import dk.binumre.startlist.StartList
import dk.binumre.startlist.StartListParser
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.prefs.Preferences

enum class Screen { START, MAIN }
enum class Page { LIST, SETUP, DESIGN }

/** Det valgte i editoren: et af startnummerets elementer eller et stykke grafik (indeks i listen). */
data class Selection(val graphic: Boolean, val index: Int)

/** Hvilke startnumre der udskrives. */
sealed interface PrintScope {
    data object All : PrintScope
    /** Kun dem der ikke er udskrevet før (fx nytilkomne). */
    data object NotPrinted : PrintScope
    data class One(val key: String) : PrintScope
}

/** Programmets tilstand: projektet, startlisterne og det der er valgt i brugerfladen. */
class AppState {
    private val prefs: Preferences? = runCatching { Preferences.userNodeForPackage(AppState::class.java) }.getOrNull()

    /** Programmets sprog følger computerens (startnummerets sprog vælges for sig under Opsætning). */
    val strings: Strings = Strings.load(Strings.systemLanguage())
    var screen by mutableStateOf(Screen.START)
    var page by mutableStateOf(Page.LIST)
    var project by mutableStateOf(BibProject())
    /** Projektet som det senest blev gemt eller åbnet – til "ikke gemt". */
    var savedProject by mutableStateOf(BibProject())
        private set
    var projectFile by mutableStateOf<File?>(null)
        private set
    /** Startlister pr. plads: plads 0 ved enkeltløb, stafet og fælles liste; ellers én pr. etape (null = mangler). */
    var lists by mutableStateOf(listOf<StartList?>())
        private set
    var message by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var lastPdf by mutableStateOf<File?>(null)
        private set
    var previewIndex by mutableStateOf(0)
    var selection by mutableStateOf<Selection?>(null)
    /** Åben udskriftsdialog (null = lukket). */
    var printRequest by mutableStateOf<PrintScope?>(null)
    val images = ImageCache()

    val zone: ZoneId get() = BibLayout.zoneOf(project)
    /** Startlisterne som de senest blev gemt eller åbnet. */
    private var savedLists by mutableStateOf(listOf<StartList?>())
    val dirty: Boolean get() = project != savedProject || lists.dropLastWhile { it == null } != savedLists.dropLastWhile { it == null }

    /** Antal pladser til startlister. */
    val slotCount: Int get() = if (project.type == BibType.MULTIDAY && !project.sharedStartList) project.stageCount else 1

    val usedLists: List<StartList?> get() = (0 until slotCount).map { lists.getOrNull(it) }

    val bibSet: BibSet by derivedStateOf {
        val used = usedLists
        if (used.all { it == null }) BibSet(emptyList(), 0) else BibBuilder.build(project, used)
    }

    /** Startnumre der ikke er udskrevet endnu – kun meningsfuldt når noget er udskrevet. */
    val notPrinted: List<Bib> get() = bibSet.bibs.filter { it.key !in project.printed }

    /** Startnummeret i forhåndsvisningen: en løber fra listen, eller et eksempel når der ingen er. */
    val previewBib: Bib get() = bibSet.bibs.getOrNull(previewIndex.coerceIn(0, maxOf(0, bibSet.bibs.size - 1))) ?: sampleBib()

    private fun sampleBib(): Bib {
        val stage = BibStage(null, "H21", "1", 7400, "S1")
        val relay = project.type == BibType.RELAY
        return Bib(
            number = "123", given = strings["sample.given"], family = strings["sample.family"], name = "",
            club = strings["sample.club"], card = 8123456, cls = "H21",
            teamName = if (relay) strings["sample.club"] + " 1" else "", leg = if (relay) "1" else "",
            stages = List(project.stageCount) { stage },
        )
    }

    var lastDir: File?
        get() = prefs?.get("dir", null)?.let(::File)?.takeIf { it.isDirectory }
        set(value) { runCatching { if (value != null) prefs?.put("dir", value.path) } }

    // ---------- Projekt ----------

    /** Tilbage til startskærmen for at vælge løbstype. */
    fun newProject() {
        project = BibProject()
        savedProject = project
        projectFile = null
        lists = emptyList()
        savedLists = lists
        selection = null
        previewIndex = 0
        message = ""
        screen = Screen.START
    }

    /** Nyt løb af typen [type] (flerdagesløb med [stages] etaper). */
    fun start(type: BibType, stages: Int = 2) {
        project = BibProject(
            type = type, language = Strings.bibLanguage(),
            stages = List(if (type == BibType.MULTIDAY) stages.coerceIn(1, MAX_STAGES) else 1) { Stage() },
        )
        savedProject = project
        projectFile = null
        lists = emptyList()
        savedLists = lists
        selection = null
        previewIndex = 0
        page = Page.LIST
        screen = Screen.MAIN
        message = ""
    }

    fun setType(type: BibType) {
        if (type == project.type) return
        val stages = if (type == BibType.MULTIDAY) List(maxOf(2, project.stages.size)) { project.stage(it) } else listOf(project.stage(0))
        project = project.copy(type = type, stages = stages)
        selection = null
    }

    fun setStageCount(count: Int) {
        val n = count.coerceIn(1, MAX_STAGES)
        project = project.copy(stages = List(n) { project.stage(it) })
        if (lists.size > n) lists = lists.take(n)
    }

    fun setShared(shared: Boolean) {
        project = project.copy(sharedStartList = shared)
    }

    fun openProject(file: File) = guarded {
        val data = ProjectFiles.load(file)
        project = data.project
        savedProject = project
        lists = data.lists
        savedLists = lists
        previewIndex = 0
        page = Page.LIST
        projectFile = file
        selection = null
        screen = Screen.MAIN
        message = strings.format("msg.opened", file.name)
    }

    fun saveProject(file: File) = guarded {
        ProjectFiles.save(project, file, lists)
        savedProject = project
        savedLists = lists
        projectFile = file
        message = strings.format("msg.saved", file.name)
    }

    // ---------- Udskrift ----------

    fun bibsFor(scope: PrintScope): List<Bib> = when (scope) {
        PrintScope.All -> bibSet.bibs
        PrintScope.NotPrinted -> notPrinted
        is PrintScope.One -> bibSet.bibs.filter { it.key == scope.key }
    }

    fun markPrinted(bibs: List<Bib>) {
        project = project.copy(printed = project.printed + bibs.map { it.key })
    }

    fun forgetPrinted() {
        project = project.copy(printed = emptySet())
    }

    fun writePdf(file: File, bibs: List<Bib> = bibSet.bibs) = guarded {
        if (bibs.isEmpty()) throw IllegalStateException(strings["error.noBibs"])
        PdfWriter.write(bibs, project, file)
        lastPdf = file
        message = strings.format("msg.pdf", bibs.size, file.name)
    }

    // ---------- Startlister ----------

    /**
     * Indlæser filer fra plads [slot]. Ved flerdagesløb med én liste pr. etape fylder flere filer de følgende
     * etaper (og antallet af etaper udvides om nødvendigt); ellers erstatter den første fil startlisten.
     */
    fun loadFiles(files: List<File>, slot: Int = 0) = guarded {
        if (files.isEmpty()) return@guarded
        val loaded = files.map { f ->
            val p = StartListParser.parse(f.readBytes(), zone)
            StartList(p.event, f.name, System.currentTimeMillis(), p.entries, date = p.date)
        }
        addLists(loaded, slot)
    }

    /** Henter startlisten fra OFeed. Kaldes fra en baggrundstråd; [addLists] med resultatet på UI-tråden. */
    fun fetchOFeed(source: OFeedSource): StartList {
        val r = OFeedClient.fetch(connection(source))
        return StartList(r.event, "OFeed", System.currentTimeMillis(), r.entries, cursor = OFeedClient.nextCursor(r.serverTime))
    }

    private fun connection(source: OFeedSource) =
        OFeedConnection.fromLink(source.link) ?: OFeedConnection(OFeedConnection.DEFAULT_URL, source.link.trim(), source.password)

    /**
     * Henter ændringer fra OFeed til plads [slot] med den gemte forbindelse (baggrundstråd). Kun ændringer siden
     * sidst hentes og flettes ind; fravalg i tabellen bevares. Resultatet sættes ind med [setList].
     */
    fun refreshOFeed(slot: Int): StartList {
        val source = project.ofeed[slot] ?: throw IllegalStateException(strings["error.noOFeed"])
        val old = lists.getOrNull(slot)
        val r = OFeedClient.fetch(connection(source), old?.cursor)
        val excluded = old?.entries.orEmpty().filter { it.excluded && it.id.isNotEmpty() }.map { it.id }.toSet()
        val fresh = r.entries.map { if (it.id in excluded) it.copy(excluded = true) else it }
        val entries = if (old?.cursor != null) mergeChanges(old.entries, fresh) else fresh
        return StartList(r.event.ifEmpty { old?.event.orEmpty() }, "OFeed", System.currentTimeMillis(), entries, OFeedClient.nextCursor(r.serverTime), old?.date)
            .also { lastChanges = r.entries.size }
    }

    /** Antal ændrede løbere ved seneste OFeed-opdatering. */
    var lastChanges = 0
        private set

    fun setList(slot: Int, list: StartList) {
        val next = MutableList(maxOf(lists.size, slot + 1)) { lists.getOrNull(it) }
        next[slot] = list
        lists = next
        message = strings.format("msg.refreshed", lastChanges, list.entries.size)
    }

    /** Liste hentet fra OFeed: forbindelsen huskes i projektet, så listen kan opdateres senere. */
    fun addOFeed(list: StartList, slot: Int, source: OFeedSource) {
        addLists(listOf(list), slot)
        val at = if (project.type == BibType.MULTIDAY && !project.sharedStartList) slot else 0
        project = project.copy(ofeed = project.ofeed + (at to source))
    }

    fun addLists(loaded: List<StartList>, slot: Int = 0) {
        if (loaded.isEmpty()) return
        val perStage = project.type == BibType.MULTIDAY && !project.sharedStartList
        val used = if (perStage) loaded else loaded.take(1)
        val start = if (perStage) slot.coerceIn(0, MAX_STAGES - 1) else 0
        val end = minOf(start + used.size, MAX_STAGES)
        if (perStage && end > project.stageCount) setStageCount(end)
        val next = MutableList(maxOf(lists.size, end)) { lists.getOrNull(it) }
        for (i in start until end) next[i] = used[i - start]
        lists = next
        // En fil erstatter en OFeed-forbindelse på samme plads
        if ((start until end).any { it in project.ofeed }) project = project.copy(ofeed = project.ofeed - (start until end).toSet())
        if (project.eventName.isEmpty()) project = project.copy(eventName = used.first().event)
        previewIndex = 0
        message = strings.format("msg.loaded", used.sumOf { it.entries.size }, used.joinToString(", ") { it.source }) +
            if (used.size < loaded.size) " " + strings.format("msg.onlyFirst", loaded.size - used.size) else ""
    }

    fun removeList(slot: Int) {
        if (slot in lists.indices) lists = lists.mapIndexed { i, l -> if (i == slot) null else l }
        if (slot in project.ofeed) project = project.copy(ofeed = project.ofeed - slot)
    }

    fun updateEntries(slot: Int, change: (Int, StartEntry) -> StartEntry) {
        lists = lists.mapIndexed { i, l -> if (i != slot || l == null) l else l.copy(entries = l.entries.mapIndexed(change)) }
    }

    /** Dagen som nye starttider i pladsen hører til. */
    fun dateOf(slot: Int): LocalDate = lists.getOrNull(slot)?.eventDate ?: LocalDate.now(zone)

    // ---------- Elementer og grafik ----------

    val elements: List<BibElement> get() = project.layout()

    /** Ændrer element [index]; standardlayoutet gemmes i projektet første gang noget ændres. */
    fun updateElement(index: Int, element: BibElement) {
        val list = project.layout().toMutableList()
        if (index !in list.indices) return
        // Foldehjørnerne er ét kvadrat, der tegnes i alle fire hjørner
        list[index] = if (element.kind == dk.binumre.model.ElementKind.FOLD) {
            val size = maxOf(element.width, element.height).coerceIn(FOLD_MIN, FOLD_MAX)
            element.copy(x = 0.0, y = 0.0, width = size, height = size)
        } else element
        project = project.copy(elements = list)
    }

    fun resetLayout() {
        project = project.copy(elements = emptyList())
        selection = null
    }

    fun addGraphic(file: File, role: GraphicRole = GraphicRole.OTHER) {
        val image = images.get(file.absolutePath)
        if (image == null) {
            error = strings.format("error.image", file.name)
            return
        }
        val existing = if (role == GraphicRole.OTHER) project.graphics.size else project.graphics.count { it.role == role }
        val frame = GraphicEdit.placeFor(role, image.width, image.height, existing, project.stageCount)
        project = project.copy(graphics = project.graphics + Graphic(file.absolutePath, 0.0, 0.0, 0.0, 0.0, role).moved(frame))
        selection = Selection(graphic = true, index = project.graphics.lastIndex)
    }

    fun updateGraphic(index: Int, graphic: Graphic) {
        if (index !in project.graphics.indices) return
        project = project.copy(graphics = project.graphics.mapIndexed { i, g -> if (i == index) graphic else g })
    }

    fun removeGraphic(index: Int) {
        if (index !in project.graphics.indices) return
        project = project.copy(graphics = project.graphics.filterIndexed { i, _ -> i != index })
        selection = null
    }

    private fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            error = e.message?.takeIf { it.isNotBlank() } ?: e.toString()
        }
    }

    companion object {
        const val MAX_STAGES = 10
        const val FOLD_MIN = 6.0
        const val FOLD_MAX = 40.0
    }
}
