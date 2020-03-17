package no.nav.dagpenger.journalføring.ferdigstill

import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.booleanType
import com.natpryce.konfig.intType
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import io.netty.util.NetUtil.getHostname
import no.finn.unleash.util.UnleashConfig
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.ktor.auth.ApiKeyVerifier
import no.nav.dagpenger.streams.KafkaCredential
import no.nav.dagpenger.streams.PacketDeserializer
import no.nav.dagpenger.streams.PacketSerializer
import no.nav.dagpenger.streams.Topic
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsConfig
import java.net.InetAddress
import java.net.UnknownHostException

private val localProperties = ConfigurationMap(
    mapOf(
        "application.httpPort" to "8080",
        "application.name" to "dagpenger-journalføring-ferdigstill",
        "application.profile" to Profile.LOCAL.toString(),
        "dp.regel.api.url" to "http://localhost:666",
        "allow.insecure.soap.requests" to false.toString(),
        "journalPostApi.url" to "http://localhost",
        "gosysApi.url" to "http://localhost",
        "kafka.bootstrap.servers" to "localhost:9092",
        "regel.api.secret" to "secret",
        "regel.api.key" to "regelKey",
        "srvdagpenger.journalforing.ferdigstill.password" to "password",
        "srvdagpenger.journalforing.ferdigstill.username" to "user",
        "sts.url" to "http://localhost",
        "soapsecuritytokenservice.url" to "http://localhost",
        "behandlearbeidsytelsesak.v1.url" to "https://localhost/ail_ws/BehandleArbeidOgAktivitetOppgave_v1",
        "ytelseskontrakt.v3.url" to "https://localhost/ail_ws/Ytelseskontrakt_v3",
        "unleash.url" to "http://localhost:1010",
        "kafka.processing.guarantee" to StreamsConfig.AT_LEAST_ONCE

    )
)
private val devProperties = ConfigurationMap(
    mapOf(
        "application.httpPort" to "8080",
        "application.name" to "dagpenger-journalføring-ferdigstill",
        "application.profile" to Profile.DEV.toString(),
        "allow.insecure.soap.requests" to true.toString(),
        "dp.regel.api.url" to "http://dp-regel-api",
        "journalPostApi.url" to "http://dokarkiv.q1.svc.nais.local",
        "gosysApi.url" to "http://oppgave.default.svc.nais.local",
        "kafka.bootstrap.servers" to "b27apvl00045.preprod.local:8443,b27apvl00046.preprod.local:8443,b27apvl00047.preprod.local:8443",
        "sts.url" to "http://security-token-service.default.svc.nais.local",
        "soapsecuritytokenservice.url" to "https://sts-q1.preprod.local/SecurityTokenServiceProvider/",
        "behandlearbeidsytelsesak.v1.url" to "https://arena-q1.adeo.no/ail_ws/BehandleArbeidOgAktivitetOppgave_v1",
        "ytelseskontrakt.v3.url" to "https://arena-q1.adeo.no/ail_ws/Ytelseskontrakt_v3",
        "unleash.url" to "http://unleash.default.svc.nais.local/api",
        "kafka.processing.guarantee" to StreamsConfig.AT_LEAST_ONCE
    )
)
private val prodProperties = ConfigurationMap(
    mapOf(
        "application.httpPort" to "8080",
        "application.name" to "dagpenger-journalføring-ferdigstill",
        "application.profile" to Profile.PROD.toString(),
        "allow.insecure.soap.requests" to true.toString(),
        "dp.regel.api.url" to "http://dp-regel-api",
        "journalPostApi.url" to "http://dokarkiv.default.svc.nais.local",
        "gosysApi.url" to "http://oppgave.default.svc.nais.local",
        "kafka.bootstrap.servers" to "a01apvl00145.adeo.no:8443,a01apvl00146.adeo.no:8443,a01apvl00147.adeo.no:8443,a01apvl00148.adeo.no:8443,a01apvl00149.adeo.no:8443,a01apvl00150.adeo.no:8443",
        "sts.url" to "http://security-token-service.default.svc.nais.local",
        "soapsecuritytokenservice.url" to "https://sts.adeo.no/SecurityTokenServiceProvider/",
        "behandlearbeidsytelsesak.v1.url" to "https://arena.adeo.no/ail_ws/BehandleArbeidOgAktivitetOppgave_v1",
        "ytelseskontrakt.v3.url" to "https://arena.adeo.no/ail_ws/Ytelseskontrakt_v3",
        "unleash.url" to "https://unleash.nais.adeo.no/api/",
        "kafka.processing.guarantee" to StreamsConfig.AT_LEAST_ONCE
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
    val auth: Auth = Auth(),
    val kafka: Kafka = Kafka(),
    val application: Application = Application(),
    val journalPostApiUrl: String = config()[Key("journalPostApi.url", stringType)],
    val gosysApiUrl: String = config()[Key("gosysApi.url", stringType)],
    val behandleArbeidsytelseSakConfig: BehandleArbeidsytelseSakConfig = BehandleArbeidsytelseSakConfig(),
    val ytelseskontraktV3Config: YtelseskontraktV3Config = YtelseskontraktV3Config(),
    val soapSTSClient: SoapSTSClient = SoapSTSClient(),
    val sts: Sts = Sts()
) {
    class Auth(
        regelApiSecret: String = config()[Key("regel.api.secret", stringType)],
        regelApiKeyPlain: String = config()[Key("regel.api.key", stringType)]
    ) {
        val regelApiKey = ApiKeyVerifier(regelApiSecret).generate(regelApiKeyPlain)
    }

    data class Kafka(
        val dagpengerJournalpostTopicV1: Topic<String, Packet> = Topic(
            "privat-dagpenger-journalpost-mottatt-v1",
            keySerde = Serdes.String(),
            valueSerde = Serdes.serdeFrom(PacketSerializer(), PacketDeserializer())
        ),
        val dagpengerJournalpostTopicV2: Topic<String, Packet> = Topic(
            "privat-dagpenger-journalpost-mottatt-v2",
            keySerde = Serdes.String(),
            valueSerde = Serdes.serdeFrom(PacketSerializer(), PacketDeserializer())
        ),
        val brokers: String = config()[Key("kafka.bootstrap.servers", stringType)],
        val credential: KafkaCredential = KafkaCredential(
            username = config()[Key("srvdagpenger.journalforing.ferdigstill.username", stringType)],
            password = config()[Key("srvdagpenger.journalforing.ferdigstill.password", stringType)]
        ),
        val processingGuarantee: String = config()[Key("kafka.processing.guarantee", stringType)]
    )

    data class Sts(
        val baseUrl: String = config()[Key("sts.url", stringType)],
        val username: String = config()[Key("srvdagpenger.journalforing.ferdigstill.username", stringType)],
        val password: String = config()[Key("srvdagpenger.journalforing.ferdigstill.password", stringType)]
    )

    data class SoapSTSClient(
        val endpoint: String = config()[Key("soapsecuritytokenservice.url", stringType)],
        val username: String = config()[Key("srvdagpenger.journalforing.ferdigstill.username", stringType)],
        val password: String = config()[Key("srvdagpenger.journalforing.ferdigstill.password", stringType)],
        val allowInsecureSoapRequests: Boolean = config()[Key("allow.insecure.soap.requests", booleanType)]
    )

    data class Application(
        val profile: Profile = config()[Key("application.profile", stringType)].let { Profile.valueOf(it) },
        val httpPort: Int = config()[Key("application.httpPort", intType)],
        val name: String = config()[Key("application.name", stringType)],
        val unleashConfig: UnleashConfig = UnleashConfig.builder()
            .appName(config().getOrElse(Key("app.name", stringType), "dagpenger-journalforing-ferdigstill"))
            .instanceId(getHostname())
            .unleashAPI(config()[Key("unleash.url", stringType)])
            .build(),
        val regelApiBaseUrl: String = config()[Key("dp.regel.api.url", stringType)]
    )

    data class BehandleArbeidsytelseSakConfig(
        val endpoint: String = config()[Key("behandlearbeidsytelsesak.v1.url", stringType)]
    )

    data class YtelseskontraktV3Config(
        val endpoint: String = config()[Key("ytelseskontrakt.v3.url", stringType)]
    )
}

enum class Profile {
    LOCAL, DEV, PROD
}

fun getHostname(): String {
    return try {
        val addr: InetAddress = InetAddress.getLocalHost()
        addr.hostName
    } catch (e: UnknownHostException) {
        "unknown"
    }
}