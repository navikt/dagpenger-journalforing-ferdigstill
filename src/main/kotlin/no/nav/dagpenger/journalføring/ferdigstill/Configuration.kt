package no.nav.dagpenger.journalføring.ferdigstill

import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import no.finn.unleash.util.UnleashConfig
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.streams.KafkaCredential
import no.nav.dagpenger.streams.PacketDeserializer
import no.nav.dagpenger.streams.PacketSerializer
import no.nav.dagpenger.streams.Topic
import org.apache.kafka.common.serialization.Serdes

private val localProperties = ConfigurationMap(
    mapOf(
        "application.httpPort" to "8080",
        "application.name" to "dagpenger-journalføring-ferdigstill",
        "application.profile" to Profile.LOCAL.toString(),
        "journalPostApi.url" to "http://localhost",
        "kafka.bootstrap.servers" to "localhost:9092",
        "srvdagpenger.journalforing.ferdigstill.password" to "password",
        "srvdagpenger.journalforing.ferdigstill.username" to "user",
        "sts.url" to "http://localhost",
        "unleash.url" to "http://localhost"

    )
)
private val devProperties = ConfigurationMap(
    mapOf(
        "application.httpPort" to "8080",
        "application.name" to "dagpenger-journalføring-ferdigstill",
        "application.profile" to Profile.DEV.toString(),
        "journalPostApi.url" to "http://dokarkiv.t8.svc.nais.local",
        "kafka.bootstrap.servers" to "b27apvl00045.preprod.local:8443,b27apvl00046.preprod.local:8443,b27apvl00047.preprod.local:8443",
        "sts.url" to "http://security-token-service.default.svc.nais.local",
        "unleash.url" to "http://unleash.default.svc.nais.local"
    )
)
private val prodProperties = ConfigurationMap(
    mapOf(
        "application.httpPort" to "8080",
        "application.name" to "dagpenger-journalføring-ferdigstill",
        "application.profile" to Profile.PROD.toString(),
        "journalPostApi.url" to "http://dokarkiv.default.svc.nais.local",
        "kafka.bootstrap.servers" to "a01apvl00145.adeo.no:8443,a01apvl00146.adeo.no:8443,a01apvl00147.adeo.no:8443,a01apvl00148.adeo.no:8443,a01apvl00149.adeo.no:8443,a01apvl00150.adeo.no:8443",
        "sts.url" to "http://security-token-service.default.svc.nais.local",
        "unleash.url" to "http://unleash.default.svc.nais.local"
    )
)

private val defaultConfiguration = ConfigurationProperties.systemProperties() overriding EnvironmentVariables

fun config(): Configuration {
    return when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
        "dev-fss" -> defaultConfiguration overriding devProperties
        "prod-fss" -> defaultConfiguration overriding prodProperties
        else -> {
            defaultConfiguration overriding localProperties
        }
    }
}

data class Configuration(
    val kafka: Kafka = Kafka(),
    val application: Application = Application(),
    val journalPostApiUrl: String = config()[Key("journalPostApi.url", stringType)],
    val sts: Sts = Sts(),
    val unleashConfig: UnleashConfig = UnleashConfig.builder()
        .appName(application.name)
        .unleashAPI(config()[Key("unleash.url", stringType)])
        .build()
) {

    data class Kafka(
        val dagpengerJournalpostTopic: Topic<String, Packet> = Topic(
            "privat-dagpenger-journalpost-mottatt-v1",
            keySerde = Serdes.String(),
            valueSerde = Serdes.serdeFrom(PacketSerializer(), PacketDeserializer())
        ),
        val brokers: String = config()[Key("kafka.bootstrap.servers", stringType)],
        val credential: KafkaCredential = KafkaCredential(
            username = config()[Key("srvdagpenger.journalforing.ferdigstill.username", stringType)],
            password = config()[Key("srvdagpenger.journalforing.ferdigstill.password", stringType)]
        )
    )

    data class Sts(
        val baseUrl: String = config()[Key("sts.url", stringType)],
        val username: String = config()[Key("srvdagpenger.journalforing.ferdigstill.username", stringType)],
        val password: String = config()[Key("srvdagpenger.journalforing.ferdigstill.password", stringType)]
    )

    data class Application(
        val profile: Profile = config()[Key("application.profile", stringType)].let { Profile.valueOf(it) },
        val httpPort: Int = config()[Key("application.httpPort", intType)],
        val name: String = config()[Key("application.name", stringType)]
    )
}

enum class Profile {
    LOCAL, DEV, PROD
}
