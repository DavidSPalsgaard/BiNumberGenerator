// PC-program (Windows, Linux, Mac): brugerflade (Compose), PDF-udskrift og kommandolinje.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
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
            packageVersion = "1.0.0"
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
                perUserInstall = true
                // Samme id i alle versioner, så en ny version erstatter den gamle
                upgradeUuid = "6f0c7a52-3c1e-4d1b-9a55-2b8e4f3d7c11"
            }
        }
    }
}
