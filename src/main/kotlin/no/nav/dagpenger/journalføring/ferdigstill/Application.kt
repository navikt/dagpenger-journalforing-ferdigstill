package no.nav.dagpenger.journalføring.ferdigstill

import mu.KotlinLogging
import mu.withLoggingContext
import no.finn.unleash.DefaultUnleash
import no.finn.unleash.Unleash
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.GosysOppgaveClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.JournalpostRestApi
import no.nav.dagpenger.journalføring.ferdigstill.adapter.soap.STS_SAML_POLICY_NO_TRANSPORT_BINDING
import no.nav.dagpenger.journalføring.ferdigstill.adapter.soap.SoapPort
import no.nav.dagpenger.journalføring.ferdigstill.adapter.soap.arena.SoapArenaClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.soap.configureFor
import no.nav.dagpenger.journalføring.ferdigstill.adapter.soap.stsClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester.Vilkårtester
import no.nav.dagpenger.oidc.StsOidcClient
import no.nav.dagpenger.streams.River
import no.nav.dagpenger.streams.streamConfigAiven
import no.nav.tjeneste.virksomhet.ytelseskontrakt.v3.YtelseskontraktV3
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.streams.StreamsConfig
import java.util.Properties

private val logger = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

internal class Application(
    private val configuration: Configuration,
    private val journalføringFerdigstill: JournalføringFerdigstill,
    private val unleash: Unleash
) : River(configuration.kafka.dagpengerJournalpostTopic) {

    override val SERVICE_APP_ID = configuration.application.name
    override val HTTP_PORT: Int = configuration.application.httpPort

    override fun filterPredicates() = listOf(erIkkeFerdigBehandletJournalpost)

    override fun onPacket(packet: Packet): Packet {
        withLoggingContext(
            "journalpost_id" to PacketMapper.journalpostIdFrom(packet)
        ) {
            logger.info { "Behandler journalpost-pakke som er lest ${packet.getStringValue("system_read_count")} ganger" }
            sikkerlogg.info {
                "Behandler journalpost for person med naturlig ident ${PacketMapper.bruker(packet)} og aktør-id ${
                PacketMapper.nullableAktørFrom(
                    packet
                )
                }"
            }

            val readCountLimit = 15
            if (packet.getReadCount() >= readCountLimit && !unleash.isEnabled("dagpenger-journalforing-ferdigstill.skipReadCount", false)) {
                logger.error {
                    "Read count >= $readCountLimit for packet with journalpostid ${packet.getStringValue(PacketKeys.JOURNALPOST_ID)}"
                }
                throw ReadCountException()
            }

            return journalføringFerdigstill.handlePacket(packet)
        }
    }

    override fun getConfig(): Properties {
        val properties = streamConfigAiven(
            appId = SERVICE_APP_ID,
            bootStapServerUrl = configuration.kafka.brokers,
            aivenCredentials = configuration.kafka.credential
        )
        properties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        properties[StreamsConfig.PROCESSING_GUARANTEE_CONFIG] = configuration.kafka.processingGuarantee
        return properties
    }

    override fun onFailure(packet: Packet, error: Throwable?): Packet {
        logger.error(error) { "Feilet ved håndtering av journalpost: ${packet.getStringValue(PacketKeys.JOURNALPOST_ID)}. Pakke $packet" }
        throw error ?: RuntimeException("Feilet ved håndtering av pakke, ukjent grunn")
    }
}

fun main() {
    val configuration = Configuration()
    val ytelseskontraktV3: YtelseskontraktV3 =
        SoapPort.ytelseskontraktV3(configuration.ytelseskontraktV3Config.endpoint)

    val behandleArbeidsytelseSak =
        SoapPort.behandleArbeidOgAktivitetOppgaveV1(configuration.behandleArbeidsytelseSakConfig.endpoint)

    val arenaClient: ArenaClient =
        SoapArenaClient(behandleArbeidsytelseSak, ytelseskontraktV3)

    val stsOidcClient = StsOidcClient(configuration.sts.baseUrl, configuration.sts.username, configuration.sts.password)

    val soapStsClient = stsClient(
        stsUrl = configuration.soapSTSClient.endpoint,
        credentials = configuration.soapSTSClient.username to configuration.soapSTSClient.password
    )
    if (configuration.soapSTSClient.allowInsecureSoapRequests) {
        soapStsClient.configureFor(behandleArbeidsytelseSak, STS_SAML_POLICY_NO_TRANSPORT_BINDING)
        soapStsClient.configureFor(ytelseskontraktV3, STS_SAML_POLICY_NO_TRANSPORT_BINDING)
    } else {
        soapStsClient.configureFor(behandleArbeidsytelseSak)
        soapStsClient.configureFor(ytelseskontraktV3)
    }

    val unleash: Unleash = DefaultUnleash(configuration.application.unleashConfig)
    val gosysOppgaveClient = GosysOppgaveClient(
        configuration.gosysApiUrl,
        stsOidcClient
    )
    val vilkårtester = Vilkårtester(configuration.application.regelApiBaseUrl, configuration.auth.regelApiKey)
    val journalFøringFerdigstill = JournalføringFerdigstill(
        JournalpostRestApi(
            configuration.journalPostApiUrl,
            stsOidcClient
        ),
        gosysOppgaveClient,
        arenaClient,
        vilkårtester
    )

    Application(configuration, journalFøringFerdigstill, unleash).start()
}

class ReadCountException : RuntimeException()
