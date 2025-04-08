import org.gradle.internal.jvm.Jvm
import org.panteleyev.jpackage.ImageType
import org.panteleyev.jpackage.JPackageTask

plugins {
    id("idea")
    id("com.gradleup.shadow") version "8.+"
    id("java")
    id("application")
    id("org.panteleyev.jpackageplugin") version "1.6.1"
}

group = "ovh.paulem.fjc"
version = "1.3.1"

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
    implementation("io.github.matyrobbrt:curseforgeapi:2.+")

    // Remove annoying warnings for slf4j
    implementation("org.slf4j:slf4j-simple:2.0.17")

    // UI
    implementation("io.github.mkpaz:atlantafx-base:2.+")
    implementation("com.github.Dansoftowner:FXTaskbarProgressBar:v11.4")

    // Core
    implementation("com.google.guava:guava:33.4.7-android")
    implementation("com.google.code.gson:gson:2.+")
    implementation("javax.xml.bind:jaxb-api:2.4.0-b180830.0359")

    // Options
    implementation("net.sf.jopt-simple:jopt-simple:6.0-alpha-3")

    // Annotations
    implementation("org.jetbrains:annotations:26.+")
}

application {
    mainClass.set("$group.Main")
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

val jvmOpts = listOf("-Dfile.encoding=UTF-8", "--add-exports=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED", "--add-opens=javafx.graphics/javafx.scene.layout=ALL-UNNAMED")

tasks.withType<JPackageTask>().configureEach {
    dependsOn(tasks.shadowJar)

    appName = rootProject.name
    appVersion = project.version.toString()
    vendor = "Paulem"
    copyright = "Copyright (c) 2025 Paulem"
    runtimeImage = Jvm.current().javaHome.toString()
    destination = "dist"
    input = "build/libs"
    mainJar = tasks.shadowJar.get().archiveFileName.get()
    mainClass = application.mainClass.get()
    javaOptions = jvmOpts
}

var infra = ""
tasks.register<JPackageTask>("zipjpackage") {
    group = tasks.jpackage.get().group

    type = ImageType.APP_IMAGE

    linux {
        infra = "linux"
    }

    mac {
        icon = "src/main/resources/assets/icons.icns"
        infra = "macos"
    }

    windows {
        icon = "src/main/resources/assets/icons.ico"

        winConsole = true
        infra = "windows"
    }

    finalizedBy("renameZip")
}

tasks.register<Zip>("renameZip") {
    group = tasks.jpackage.get().group
    archiveFileName.set(infra + "-FlowJsonCreator-" + project.version + ".zip")
    destinationDirectory.set(layout.projectDirectory.dir("dist"))

    from(layout.projectDirectory.dir("dist/FlowJsonCreator"))
}

tasks.jpackage {
    linux {
        type = ImageType.DEB
    }

    mac {
        icon = "src/main/resources/assets/icons.icns"

        type = ImageType.DMG
    }

    windows {
        icon = "src/main/resources/assets/icons.ico"

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
    jvmArgs(jvmOpts)

    finalizedBy(tasks.clean)
}