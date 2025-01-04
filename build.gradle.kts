import org.gradle.internal.jvm.Jvm
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.panteleyev.jpackage.ImageType

plugins {
    id("idea")
    id("io.github.goooler.shadow") version "8.+"
    id("java")
    id("application")
    id("org.panteleyev.jpackageplugin") version "1.6.0"
}

group = "io.github.paulem.fjc"
version = "1.3"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    // API dependencies
    implementation("io.github.matyrobbrt:curseforgeapi:1.+")

    // UI
    implementation("io.github.mkpaz:atlantafx-base:2.+")

    // Core
    implementation("com.google.guava:guava:33.4.0-jre")
    implementation("com.google.code.gson:gson:2.+")
    implementation("javax.xml.bind:jaxb-api:2.4.0-b180830.0359")

    // Options
    implementation("net.sf.jopt-simple:jopt-simple:6.0-alpha-3")

    // Annotations
    implementation("org.jetbrains:annotations:24.+")
}

application {
    mainClass.set("io.github.paulem.fjc.gui.Main")
}

tasks.withType<JavaCompile>().configureEach {
    JavaVersion.VERSION_17.toString().also {
        sourceCompatibility = it
        targetCompatibility = it
    }
    options.encoding = "UTF-8"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.BELLSOFT
    }
}

tasks.jar {
    mustRunAfter(tasks.clean)
    finalizedBy(tasks.shadowJar)
}

tasks.shadowJar {
    mustRunAfter(tasks.distZip)
    mustRunAfter(tasks.distTar)
    mustRunAfter(tasks.startScripts)

    archiveVersion.set("")
    archiveClassifier.set("")
}

tasks.register<Delete>("deleteDist") {
    delete("dist")
}

tasks.clean {
    dependsOn("deleteDist")
}

tasks.jpackage {
    dependsOn(tasks.clean, tasks.build)

    appName = "FlowJsonCreator"
    appVersion = project.version.toString()
    vendor = "Paulem"
    copyright = "Copyright (c) 2025 Paulem"
    runtimeImage = Jvm.current().javaHome.toString()
    destination = "dist"
    input = "build/libs"
    mainJar = tasks.shadowJar.get().archiveFileName.get()
    mainClass = application.mainClass.get()
    javaOptions = listOf("-Dfile.encoding=UTF-8")

    linux {
        type = ImageType.DEB
    }

    mac {
        icon = "icons/icons.icns"

        type = ImageType.DMG
    }

    windows {
        icon = "icons/icons.ico"

        type = ImageType.MSI

        winConsole = true
        winShortcut = true
        winShortcutPrompt = true
        if(type == ImageType.EXE || type == ImageType.MSI) {
            winMenu = true
            winDirChooser = true
            winPerUserInstall = true
            // winUpdateUrl can be interesting for auto-updates
        }
    }
}

tasks.register<JavaExec>("runShadowJar") {
    val javaPath = Jvm.current().javaExecutable.toString()

    group = "application"
    description = "Builds and runs the shadow jar using the specified Java path"

    dependsOn(tasks.shadowJar)

    classpath = files(tasks.shadowJar.get().archiveFile)
    setExecutable(javaPath)
}