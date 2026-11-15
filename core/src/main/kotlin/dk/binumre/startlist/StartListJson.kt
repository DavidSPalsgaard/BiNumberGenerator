package dk.binumre.startlist

import org.json.JSONArray
import org.json.JSONObject

/** Startlister som JSON – så projektfilen kan rumme startlisterne med alle rettelser og gives videre. */
object StartListJson {

    fun toJson(list: StartList): JSONObject = JSONObject().apply {
        put("event", list.event)
        put("source", list.source)
        put("loadedAt", list.loadedAt)
        list.cursor?.let { put("cursor", it) }
        list.date?.let { put("date", it) }
        put("entries", JSONArray(list.entries.map(::entry)))
    }

    fun fromJson(o: JSONObject): StartList = StartList(
        event = o.optString("event"),
        source = o.optString("source"),
        loadedAt = o.optLong("loadedAt", 0L),
        entries = o.optJSONArray("entries")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::entry) } }.orEmpty(),
        cursor = o.optString("cursor").ifEmpty { null },
        date = o.optString("date").ifEmpty { null },
    )

    /** Kun felter der ikke er tomme, så filen holdes lille. */
    private fun entry(e: StartEntry): JSONObject = JSONObject().apply {
        fun s(k: String, v: String) { if (v.isNotEmpty()) put(k, v) }
        s("id", e.id); s("given", e.given); s("family", e.family); s("name", e.name); s("club", e.club)
        s("class", e.cls); s("bib", e.bib); s("course", e.course); s("startName", e.startName); s("team", e.team)
        s("teamName", e.teamName); s("leg", e.leg); s("status", e.status)
        e.start?.let { put("start", it) }
        e.card?.let { put("card", it) }
        e.courseLength?.let { put("courseLength", it) }
        e.race?.let { put("race", it) }
        if (e.excluded) put("excluded", true)
    }

    private fun entry(o: JSONObject): StartEntry = StartEntry(
        id = o.optString("id"), given = o.optString("given"), family = o.optString("family"), name = o.optString("name"),
        club = o.optString("club"), cls = o.optString("class"), bib = o.optString("bib"),
        start = if (o.has("start")) o.optLong("start") else null,
        card = if (o.has("card")) o.optLong("card") else null,
        course = o.optString("course"), startName = o.optString("startName"), team = o.optString("team"),
        teamName = o.optString("teamName"), leg = o.optString("leg"),
        courseLength = if (o.has("courseLength")) o.optInt("courseLength") else null,
        status = o.optString("status"), excluded = o.optBoolean("excluded", false),
        race = if (o.has("race")) o.optInt("race") else null,
    )
}
