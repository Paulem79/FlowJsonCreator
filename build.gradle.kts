import org.gradle.internal.jvm.Jvm
import org.panteleyev.jpackage.ImageType
import org.panteleyev.jpackage.JPackageTask

plugins {
    id("idea")
    id("com.gradleup.shadow") version "8.+"
    id("java")
    id("application")
    id("org.panteleyev.jpackageplugin") version "1.6.0"
}

group = "ovh.paulem.fjc"
version = "1.3"

repositories {
    mavenCentral()
    mavenLocal()

    maven { url = uri("https://jitpack.io") }
    maven {
        url = uri("https://maven.paulem.ovh/releases")
    }
}

dependencies {
    // API dependencies
    implementation("ovh.paulem:modrinthapi:1.+")
    implementation("io.github.matyrobbrt:curseforgeapi:1.+")

    // Remove annoying warnings for slf4j
    implementation("org.slf4j:slf4j-simple:1.7.36")

    // UI
    implementation("io.github.mkpaz:atlantafx-base:2.+")
    implementation("com.github.Dansoftowner:FXTaskbarProgressBar:v11.4")

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
    mainClass.set("ovh.paulem.fjc.gui.Main")
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

tasks.withType<JPackageTask>().configureEach {
    dependsOn(tasks.shadowJar)

    appName = "FlowJsonCreator"
    appVersion = project.version.toString()
    vendor = "Paulem"
    copyright = "Copyright (c) 2025 Paulem"
    runtimeImage = Jvm.current().javaHome.toString()
    destination = "dist"
    input = "build/libs"
    mainJar = tasks.shadowJar.get().archiveFileName.get()
    mainClass = application.mainClass.get()
    javaOptions = listOf("-Dfile.encoding=UTF-8", "--add-exports=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED")
}

var infra = ""
tasks.register<JPackageTask>("zipjpackage") {
    finalizedBy("zipPackage")

    type = ImageType.APP_IMAGE

    linux {
        infra = "linux"
    }

    mac {
        icon = "icons/icons.icns"
        infra = "macos"
    }

    windows {
        icon = "icons/icons.ico"

        winConsole = true
        infra = "windows"
    }
}

tasks.register<Zip>("zipPackage") {
    archiveFileName.set(infra + "-FlowJsonCreator-" + project.version + ".zip")
    destinationDirectory.set(layout.projectDirectory.dir("dist"))

    from(layout.projectDirectory.dir("dist/FlowJsonCreator"))
}

tasks.jpackage {
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
        if(type == ImageType.EXE || type == ImageType.MSI) {
            winMenu = true
            winDirChooser = true
            winPerUserInstall = true
            winShortcut = true
            winShortcutPrompt = true
            // winUpdateUrl can be interesting for auto-updates
        }
    }
}

tasks.clean {
    dependsOn("deleteDist")
}

tasks.jar {
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

tasks.register<JavaExec>("runShadowJar") {
    val javaPath = Jvm.current().javaExecutable.toString()

    group = "application"
    description = "Builds and runs the shadow jar using the specified Java path"

    dependsOn(tasks.shadowJar)

    classpath = files(tasks.shadowJar.get().archiveFile)
    setExecutable(javaPath)
    jvmArgs("--add-exports=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED")

    finalizedBy(tasks.clean)
}