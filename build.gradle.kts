import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

plugins {
    id("idea")
    id("io.github.goooler.shadow") version "8.+"
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.+"
}
apply(plugin = "org.openjfx.javafxplugin")

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

javafx {
    version = "22.+"
    modules("javafx.controls")
}

tasks.compileJava {
    JavaVersion.VERSION_17.toString().also {
        sourceCompatibility = it
        targetCompatibility = it
    }
    options.encoding = "UTF-8"
}

tasks.jar {
    finalizedBy(tasks.shadowJar)
}

tasks.shadowJar {
    archiveClassifier.set("")
}

configurations.matching { it.name.contains("downloadSources") }
    .configureEach {
        attributes {
            val os = DefaultNativePlatform.getCurrentOperatingSystem().toFamilyName()
            val arch = DefaultNativePlatform.getCurrentArchitecture().name
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class, Usage.JAVA_RUNTIME))
            attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, objects.named(OperatingSystemFamily::class, os))
            attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, objects.named(MachineArchitecture::class, arch))
        }
    }

tasks.register<JavaExec>("runShadowJar") {
    val javaPath = "C:\\Program Files\\Zulu\\zulu-23\\bin\\java.exe"
    val cfKey = "\$2a\$10\$pEf8ZqqpXN3mWm.nZgjA0.dvobnxeWxPeffkd9dHBEabweZQhvqKi"

    group = "application"
    description = "Builds and runs the shadow jar using the specified Java path"

    dependsOn(tasks.shadowJar)

    classpath = files(tasks.shadowJar.get().archiveFile)
    setExecutable(javaPath)
    args("-cfKey", cfKey)
}