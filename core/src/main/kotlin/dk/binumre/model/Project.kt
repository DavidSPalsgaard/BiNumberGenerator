package dk.binumre.model

import dk.binumre.layout.Align
import dk.binumre.layout.BibLayout
import dk.binumre.startlist.NameFormat
import org.json.JSONArray
import org.json.JSONObject

enum class BibType { SINGLE, RELAY, MULTIDAY }

enum class Sheet { A4_2UP, A5 }

enum class SortBy { BIB, CLUB, CLASS, NAME }

/** Et stykke grafik (logo, sponsor …) på startnummeret. Mål i mm fra øverste venstre hjørne. */
data class Graphic(val file: String, val x: Double, val y: Double, val width: Double, val height: Double) {
    val frame get() = Frame(x, y, width, height)
    fun moved(f: Frame) = copy(x = f.x, y = f.y, width = f.width, height = f.height)
}

/** Ét løb/én etape: overskrift i bunden og afstand til start pr. startsted ("" = alle startsteder). */
data class Stage(val label: String = "", val distances: Map<String, String> = emptyMap()) {
    fun distance(startName: String): String = distances[startName] ?: distances[""] ?: ""
}

/** Opsætningen af et løb: skabelon, grafik, farver og tekster. Gemmes som JSON. */
data class BibProject(
    val type: BibType = BibType.SINGLE,
    val eventName: String = "",
    /** Sprog på selve startnummeret (uafhængigt af programmets sprog). */
    val language: String = "da",
    val nameFormat: NameFormat = NameFormat.GIVEN_FAMILY,
    val sheet: Sheet = Sheet.A4_2UP,
    val sortBy: SortBy = SortBy.BIB,
    /** Tidszone for starttider (IANA-navn); tom = computerens. */
    val zone: String = "",
    val graphics: List<Graphic> = emptyList(),
    /** Stafet: farve pr. tur (#RRGGBB). Flere ture end farver → farverne gentages. */
    val legColors: List<String> = DEFAULT_LEG_COLORS,
    /** Enkeltløb/stafet: ét element. Flerdagesløb: ét pr. etape (antallet er antal etaper). */
    val stages: List<Stage> = emptyList(),
    /** Placering af startnummerets elementer. Tom = standardlayoutet for skabelonen. */
    val elements: List<BibElement> = emptyList(),
    /** Felterne i løberens felt (INFO). */
    val infoFields: Set<InfoField> = InfoField.entries.toSet(),
    /** Flerdagesløb: én startliste til alle etaper (i stedet for én pr. etape). */
    val sharedStartList: Boolean = false,
    /** Startnumre der er udskrevet ([Bib.key]) – så nytilkomne kan udskrives for sig. */
    val printed: Set<String> = emptySet(),
) {
    fun stage(index: Int): Stage = stages.getOrNull(index) ?: Stage()

    /** Antal etaper (1 undtagen ved flerdagesløb). */
    val stageCount: Int get() = if (type == BibType.MULTIDAY) maxOf(1, stages.size) else 1

    /** Elementerne der tegnes: de gemte, eller standardlayoutet. */
    fun layout(): List<BibElement> = elements.ifEmpty { BibLayout.defaultElements(type, stageCount) }

    val customLayout: Boolean get() = elements.isNotEmpty()

    fun legColor(leg: String): Int {
        val n = leg.filter { it.isDigit() }.toIntOrNull() ?: 1
        if (legColors.isEmpty()) return 0xFFFFFF
        return parseColor(legColors[(n - 1).mod(legColors.size)])
    }

    fun toJson(): String = JSONObject().apply {
        put("type", type.name)
        put("eventName", eventName)
        put("language", language)
        put("nameFormat", nameFormat.name)
        put("sheet", sheet.name)
        put("sortBy", sortBy.name)
        put("zone", zone)
        put("graphics", JSONArray(graphics.map {
            JSONObject().put("file", it.file).put("x", it.x).put("y", it.y).put("width", it.width).put("height", it.height)
        }))
        put("legColors", JSONArray(legColors))
        put("stages", JSONArray(stages.map { JSONObject().put("label", it.label).put("distances", JSONObject(it.distances)) }))
        put("elements", JSONArray(elements.map {
            JSONObject().put("kind", it.kind.name).put("x", it.x).put("y", it.y).put("width", it.width).put("height", it.height)
                .put("visible", it.visible).put("rotated", it.rotated).put("align", it.align.name)
                .also { o -> it.color?.let { c -> o.put("color", hex(c)) }; it.background?.let { c -> o.put("background", hex(c)) } }
        }))
        put("infoFields", JSONArray(InfoField.entries.filter { it in infoFields }.map { it.name }))
        put("sharedStartList", sharedStartList)
        put("printed", JSONArray(printed.sorted()))
    }.toString(2)

    companion object {
        val DEFAULT_LEG_COLORS = listOf("#FFD400", "#3B82F6", "#22C55E", "#EF4444", "#F97316", "#A855F7", "#14B8A6", "#EC4899")

        fun fromJson(json: String): BibProject {
            val o = JSONObject(json)
            val d = BibProject()
            return BibProject(
                type = o.optString("type").uppercase().let { n -> BibType.entries.firstOrNull { it.name == n } } ?: d.type,
                eventName = o.optString("eventName"),
                language = o.optString("language", d.language),
                nameFormat = NameFormat.entries.firstOrNull { it.name == o.optString("nameFormat") } ?: d.nameFormat,
                sheet = Sheet.entries.firstOrNull { it.name == o.optString("sheet") } ?: d.sheet,
                sortBy = SortBy.entries.firstOrNull { it.name == o.optString("sortBy") } ?: d.sortBy,
                zone = o.optString("zone"),
                graphics = o.optJSONArray("graphics").objects().map {
                    Graphic(it.getString("file"), it.optDouble("x", 0.0), it.optDouble("y", 0.0), it.optDouble("width", 30.0), it.optDouble("height", 30.0))
                },
                legColors = o.optJSONArray("legColors")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: d.legColors,
                stages = o.optJSONArray("stages").objects().map { s ->
                    val dist = s.optJSONObject("distances")
                    Stage(s.optString("label"), dist?.keySet()?.associateWith { dist.optString(it) } ?: emptyMap())
                },
                elements = o.optJSONArray("elements").objects().mapNotNull { e ->
                    val kind = ElementKind.entries.firstOrNull { it.name == e.optString("kind") } ?: return@mapNotNull null
                    BibElement(
                        kind, e.optDouble("x", 0.0), e.optDouble("y", 0.0), e.optDouble("width", 20.0), e.optDouble("height", 10.0),
                        visible = e.optBoolean("visible", true), rotated = e.optBoolean("rotated", false),
                        align = Align.entries.firstOrNull { it.name == e.optString("align") } ?: Align.CENTER,
                        color = e.optString("color").ifEmpty { null }?.let(::parseColor),
                        background = e.optString("background").ifEmpty { null }?.let(::parseColor),
                    )
                },
                infoFields = o.optJSONArray("infoFields")?.let { a ->
                    (0 until a.length()).mapNotNull { i -> InfoField.entries.firstOrNull { it.name == a.optString(i) } }.toSet()
                } ?: d.infoFields,
                sharedStartList = o.optBoolean("sharedStartList", false),
                printed = o.optJSONArray("printed")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() } ?: emptySet(),
            )
        }

        fun hex(color: Int): String = "#%06X".format(color and 0xFFFFFF)

        /** "#RRGGBB" → 0xRRGGBB (ukendt tekst giver hvid). */
        fun parseColor(hex: String): Int = hex.trim().removePrefix("#").toIntOrNull(16)?.and(0xFFFFFF) ?: 0xFFFFFF

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    }
}
