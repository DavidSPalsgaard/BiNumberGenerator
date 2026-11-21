// PC-program (Windows, Linux, Mac): brugerflade (Compose), PDF-udskrift og kommandolinje.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

/** Programmets version – vises under "Om" og bruges til installationspakkerne. */
val appVersion = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

tasks.processResources {
    inputs.property("version", appVersion)
    filesMatching("app.properties") { expand("version" to appVersion) }
}

dependencies {
    implementation(project(":core"))
    implementation("org.apache.pdfbox:pdfbox:3.0.8")
    // Pakkerne bygges på hvert styresystem for sig (se .github/workflows/build.yml)
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    testImplementation("junit:junit:4.13.2")
}

// Selvstændige pakker med indbygget Java-runtime – brugeren skal ikke installere noget.
//   gradlew :desktop:createDistributable   -> build/compose/binaries/main/app/BiNumreGenerator/ (mappe med .exe)
//   gradlew :desktop:packageMsi            -> installationsfil til Windows (kun på Windows)
compose.desktop {
    application {
        mainClass = "dk.binumre.desktop.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            packageName = "BiNumreGenerator"
            packageVersion = appVersion
            description = "Startnumre til orienteringsløb"
            vendor = "BiNumreGenerator"
            modules("java.desktop", "java.prefs", "java.instrument", "jdk.unsupported", "java.xml", "java.naming", "java.net.http", "jdk.crypto.ec", "jdk.charsets", "jdk.localedata")
            linux { iconFile.set(project.file("icons/icon-512.png")) }
            macOS { iconFile.set(project.file("icons/icon.icns")) }
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menu = true
                menuGroup = "BiNumreGenerator"
                shortcut = true
                dirChooser = true
                // Installationen spørger om genvej på skrivebordet og i startmenuen (se nedenfor)
                perUserInstall = true
                // Samme id i alle versioner, så en ny version erstatter den gamle
                upgradeUuid = "6f0c7a52-3c1e-4d1b-9a55-2b8e4f3d7c11"
            }
        }
    }
}

// MSI: en side i installationen med afkrydsning for genvej på skrivebordet og i startmenuen
// (jpackage --win-shortcut-prompt; Compose har ingen indstilling for det).
tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask>().configureEach {
    if (targetFormat == org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi) {
        freeArgs.add("--win-shortcut-prompt")
    }
}
