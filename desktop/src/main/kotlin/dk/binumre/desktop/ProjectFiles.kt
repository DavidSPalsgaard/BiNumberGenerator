package dk.binumre.desktop

import dk.binumre.model.BibProject
import dk.binumre.startlist.StartList
import dk.binumre.startlist.StartListJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/** Et projekt med dets startlister – det der står i én projektfil. */
data class ProjectData(val project: BibProject, val lists: List<StartList?> = emptyList())

/**
 * Projektfilen (JSON) rummer alt: opsætningen, layoutet, startlisterne med rettelser og selve billederne
 * (indlejret), så filen kan gives videre til andre. Ved åbning pakkes billederne ud i en mappe på computeren.
 * Ældre filer uden indlejrede billeder bruger stierne relativt til projektfilens mappe.
 */
object ProjectFiles {
    /** Højst 25 MB pr. indlejret billede (et logo fylder typisk under 1 MB). */
    private const val MAX_IMAGE = 25 * 1024 * 1024

    fun save(project: BibProject, file: File, lists: List<StartList?> = emptyList()) {
        val root = JSONObject(project.copy(graphics = project.graphics.map { it.copy(file = File(it.file).name) }).toJson())
        val graphics = root.optJSONArray("graphics")
        project.graphics.forEachIndexed { i, g ->
            val f = File(g.file)
            if (f.isFile) graphics?.optJSONObject(i)?.put("data", Base64.getEncoder().encodeToString(f.readBytes()))
        }
        root.put("startLists", JSONArray(lists.mapIndexedNotNull { slot, l -> l?.let { StartListJson.toJson(it).put("slot", slot) } }))
        val tmp = File(file.absoluteFile.parentFile, file.name + ".tmp")
        tmp.writeText(root.toString(1))
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
        }
    }

    fun load(file: File, imageDir: File = defaultImageDir()): ProjectData {
        val text = file.readText()
        val root = JSONObject(text)
        val project = BibProject.fromJson(text)
        val dir = file.absoluteFile.parentFile
        val graphics = root.optJSONArray("graphics")
        val resolved = project.graphics.mapIndexed { i, g ->
            val data = graphics?.optJSONObject(i)?.optString("data").orEmpty()
            if (data.isNotEmpty()) {
                val bytes = runCatching { Base64.getDecoder().decode(data) }.getOrNull()
                if (bytes == null || bytes.size > MAX_IMAGE) g.copy(file = "") else g.copy(file = unpack(bytes, g.file, imageDir).path)
            }
            else {
                val f = File(g.file)
                g.copy(file = (if (f.isAbsolute || dir == null) f else File(dir, g.file)).absoluteFile.normalize().path)
            }
        }
        val lists = mutableListOf<StartList?>()
        root.optJSONArray("startLists")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val slot = o.optInt("slot", i)
                while (lists.size <= slot) lists += null
                lists[slot] = StartListJson.fromJson(o)
            }
        }
        return ProjectData(project.copy(graphics = resolved), lists)
    }

    /** Billedet gemmes under sit indholds-fingeraftryk, så samme billede kun ligger der én gang. */
    private fun unpack(bytes: ByteArray, name: String, dir: File): File {
        dir.mkdirs()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(24)
        // Kun kendte billedtyper som filendelse – filnavnet kommer fra en fil, der kan være lavet af andre
        val ext = File(name).extension.lowercase().takeIf { it in setOf("png", "jpg", "jpeg") } ?: "png"
        val out = File(dir, "$hash.$ext")
        if (!out.isFile || out.length() != bytes.size.toLong()) out.writeBytes(bytes)
        return out
    }

    /** Windows: %LOCALAPPDATA%\BiNumreGenerator\grafik; ellers ~/.cache/BiNumreGenerator/grafik. */
    fun defaultImageDir(): File {
        val base = System.getenv("LOCALAPPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".cache")
        return File(base, "BiNumreGenerator/grafik")
    }
}
