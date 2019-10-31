import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    application
    kotlin("jvm") version Kotlin.version
    id(Spotless.spotless) version Spotless.version
    id(Shadow.shadow) version Shadow.version
}

buildscript {
    repositories {
        jcenter()
    }
}

apply {
    plugin(Spotless.spotless)
}

repositories {
    mavenCentral()
    jcenter()
    maven("http://packages.confluent.io/maven/")
    maven("https://jitpack.io")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

application {
    applicationName = "dagpenger-journalforing-ferdigstill"
    mainClassName = "no.nav.dagpenger.journalføring.ferdigstill.JournalføringFerdigstill"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(Dagpenger.Events)
    implementation("com.github.navikt:dagpenger-streams:2019.10.18-12.06.fbbb66cd150b")
    implementation(Kotlin.Logging.kotlinLogging)
    implementation(Prometheus.common)
    implementation(Prometheus.hotspot)
    implementation(Konfig.konfig)
    implementation(Ktor.serverNetty)
    implementation(Log4j2.api)
    implementation(Log4j2.core)
    implementation(Log4j2.slf4j)
    implementation(Log4j2.Logstash.logstashLayout)

    api(Kafka.clients)
    api(Kafka.streams)
    api(Kafka.Confluent.avroStreamSerdes)

    testImplementation(Junit5.api)
    testImplementation(Junit5.kotlinRunner)
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation(KafkaEmbedded.env)

    testRuntimeOnly(Junit5.engine)
}

spotless {
    kotlin {
        ktlint(Klint.version)
    }
    kotlinGradle {
        target("*.gradle.kts", "additionalScripts/*.gradle.kts")
        ktlint(Klint.version)
    }
}

tasks.named("compileKotlin") {
    dependsOn("spotlessCheck")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        showExceptions = true
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.FULL
        events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}
