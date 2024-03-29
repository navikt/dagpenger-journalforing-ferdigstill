import Mockk.mockk
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
    maven("https://packages.confluent.io/maven/")
    maven("https://jitpack.io")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

application {
    applicationName = "dagpenger-journalforing-ferdigstill"
    mainClassName = "no.nav.dagpenger.journalføring.ferdigstill.ApplicationKt"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

configurations {
    all {
        resolutionStrategy {
            force("com.fasterxml.jackson.core:jackson-databind:2.12.1")
            force("com.fasterxml.jackson.core:jackson-core:2.12.1")
            // Nyere versjoner skaper trøbbel med cfx.
            force("org.codehaus.woodstox:stax2-api:3.1.4")
        }
    }
}

val cxfVersion = "3.4.1"
val tjenestespesifikasjonerVersion = "1.2019.09.25-00.21-49b69f0625e0"
val resilience4jVersion = "1.3.1"
val resultVersion = "3.0.0"

fun tjenestespesifikasjon(name: String) = "no.nav.tjenestespesifikasjoner:$name:$tjenestespesifikasjonerVersion"

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    // ktor utils
    implementation("com.github.navikt:dp-biblioteker:2019.11.14-12.52.2f5a90180072")

    // feature toggle
    implementation("no.finn.unleash:unleash-client-java:4.2.1") {
        exclude("org.apache.logging.log4j")
    }

    // httpclient
    // We need PATCH. SEE https://github.com/kittinunf/fuel/pull/562
    implementation("com.github.kittinunf.fuel:fuel") {
        version {
            strictly("f16bd8e30c")
        }
    }

    constraints {
        implementation("commons-collections:commons-collections:3.2.2") {
            because("Because Snyk says so")
        }
    }

    // kafka
    implementation(Dagpenger.Streams)
    implementation(Dagpenger.Events)
    api(Kafka.clients)
    api(Kafka.streams)
    api(Kafka.Confluent.avroStreamSerdes)

    // json
    implementation(Fuel.fuelMoshi)
    implementation(Moshi.moshi)
    implementation(Moshi.moshiKotlin)
    implementation(Moshi.moshiAdapters)
    implementation(Jackson.core)
    implementation(Jackson.jsr310)
    implementation(Jackson.kotlin)

    // prometheus
    implementation(Prometheus.common)
    implementation(Prometheus.hotspot)
    implementation(Konfig.konfig)
    implementation(Ktor.serverNetty)

    // logging
    implementation(Kotlin.Logging.kotlinLogging)
    api("ch.qos.logback:logback-classic:1.2.3")
    api("net.logstash.logback:logstash-logback-encoder:6.4") {
        exclude("com.fasterxml.jackson.core")
    }
    implementation(Ulid.ulid)

    // resilience
    implementation("io.github.resilience4j:resilience4j-retry:$resilience4jVersion")

    // result
    implementation("com.github.kittinunf.result:result:$resultVersion")

    // testing
    testImplementation(Junit5.api)
    testImplementation(KoTest.runner)
    testImplementation(KoTest.assertions)
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation(mockk)
    testImplementation("com.gregwoodfill.assert:kotlin-json-assert:0.1.0")
    testImplementation(Kafka.streamTestUtils)
    testImplementation(Wiremock.standalone)

    testRuntimeOnly(Junit5.engine)

    // Soap stuff
    implementation("javax.xml.ws:jaxws-api:2.3.1")
    implementation("com.sun.xml.ws:jaxws-tools:2.3.0.2")

    implementation(tjenestespesifikasjon("behandleArbeidOgAktivitetOppgave-v1-tjenestespesifikasjon"))
    implementation(tjenestespesifikasjon("ytelseskontrakt-v3-tjenestespesifikasjon"))

    implementation("org.apache.cxf:cxf-rt-features-logging:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-frontend-jaxws:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-ws-policy:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-ws-security:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-transports-http:$cxfVersion")
    implementation("javax.activation:activation:1.1.1")
    implementation("no.nav.helse:cxf-prometheus-metrics:dd7d125")
    testImplementation("org.apache.cxf:cxf-rt-transports-http:$cxfVersion")
    // Soap stuff end
}

spotless {
    kotlin {
        ktlint(Ktlint.version)
    }
    kotlinGradle {
        target("*.gradle.kts", "buildSrc/**/*.kt*")
        ktlint(Ktlint.version)
    }
}

/*tasks.named("compileKotlin") {
    dependsOn("spotlessCheck")
}*/

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        showExceptions = true
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.FULL
        events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}

tasks.withType<ShadowJar> {
    mergeServiceFiles()

    // Make sure the cxf service files are handled correctly so that the SOAP services work.
    // Ref https://stackoverflow.com/questions/45005287/serviceconstructionexception-when-creating-a-cxf-web-service-client-scalajava
    transform(ServiceFileTransformer::class.java) {
        setPath("META-INF/cxf")
        include("bus-extensions.txt")
    }
}

tasks.named("shadowJar") {
    dependsOn("test")
}
