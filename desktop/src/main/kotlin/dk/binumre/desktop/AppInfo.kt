package dk.binumre.desktop

import java.util.Properties

/** Programmets navn, version, hjemmeside og ophavsret. */
object AppInfo {
    const val NAME = "BiNumreGenerator"
    /** Hjælp, nyheder og nye versioner. */
    const val WEBSITE = "https://orientering.palsgaard.net"
    const val COPYRIGHT = "Copyright © 2026 David S. Palsgaard"

    val version: String by lazy {
        runCatching {
            Properties().apply { AppInfo::class.java.getResourceAsStream("/app.properties")!!.use(::load) }.getProperty("version")
        }.getOrNull()?.takeIf { !it.startsWith("$") } ?: "udvikling"
    }

    /** En tekst fra programmets licensmappe. */
    fun license(name: String): String =
        AppInfo::class.java.getResourceAsStream("/licenses/$name")?.use { String(it.readBytes(), Charsets.UTF_8) }.orEmpty()
}
