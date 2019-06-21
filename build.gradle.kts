plugins {
    id("application")
    kotlin("jvm") version "1.3.21"
    id("com.diffplug.gradle.spotless") version "3.13.0"
    id("java-library")
}

apply {
    plugin("com.diffplug.gradle.spotless")
}

repositories {
    jcenter()
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("http://packages.confluent.io/maven")
    maven("https://dl.bintray.com/kotlin/ktor")
    maven("https://dl.bintray.com/kotlin/kotlinx")
    maven("https://dl.bintray.com/kittinunf/maven")
    maven("https://jitpack.io")
}

application {
    applicationName = "dagpenger-journalforing-ferdigstill"
    mainClassName = "no.nav.dagpenger.journalføring.ferdigstill.JournalføringFerdigstill"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

val kotlinLoggingVersion = "1.6.22"
val kafkaVersion = "2.0.1"
val confluentVersion = "4.1.2"
val ktorVersion = "1.0.0"
val prometheusVersion = "0.6.0"
val fuelVersion = "2.1.0"
val log4j2Version = "2.11.1"

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.github.navikt:dagpenger-streams:2019.06.21-11.13.27b0917e56b9")
    implementation("com.github.navikt:dagpenger-events:2019.05.20-11.56.33cd4c73a439")

    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")

    implementation("com.github.kittinunf.fuel:fuel:$fuelVersion")
    implementation("com.github.kittinunf.fuel:fuel-gson:$fuelVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")

    api("org.apache.kafka:kafka-clients:$kafkaVersion")
    api("org.apache.kafka:kafka-streams:$kafkaVersion")
    api("io.confluent:kafka-streams-avro-serde:$confluentVersion")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")

    implementation("org.apache.logging.log4j:log4j-api:$log4j2Version")
    implementation("org.apache.logging.log4j:log4j-core:$log4j2Version")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4j2Version")
    implementation("com.vlkan.log4j2:log4j2-logstash-layout-fatjar:0.15")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.12")
    testImplementation("no.nav:kafka-embedded-env:2.0.2")
}

spotless {
    kotlin {
        ktlint("0.31.0")
    }
    kotlinGradle {
        target("*.gradle.kts", "additionalScripts/*.gradle.kts")
        ktlint("0.31.0")
    }
}
