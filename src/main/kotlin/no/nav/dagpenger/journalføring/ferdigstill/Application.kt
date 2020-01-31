package no.nav.dagpenger.journalføring.ferdigstill

import mu.KotlinLogging
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.soap.STS_SAML_POLICY_NO_TRANSPORT_BINDING
import no.nav.dagpenger.journalføring.ferdigstill.adapter.soap.SoapPort
import no.nav.dagpenger.journalføring.ferdigstill.adapter.soap.arena.SoapArenaClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.soap.configureFor
import no.nav.dagpenger.journalføring.ferdigstill.adapter.soap.stsClient
import no.nav.dagpenger.oidc.StsOidcClient
import no.nav.dagpenger.streams.River
import no.nav.dagpenger.streams.streamConfig
import no.nav.tjeneste.virksomhet.ytelseskontrakt.v3.YtelseskontraktV3
import org.apache.kafka.streams.StreamsConfig
import java.util.Properties

private val logger = KotlinLogging.logger {}

internal class Application(
    private val configuration: Configuration,
    private val journalføringFerdigstill: JournalføringFerdigstill
) : River(configuration.kafka.dagpengerJournalpostTopic) {

    override val SERVICE_APP_ID = configuration.application.name
    override val HTTP_PORT: Int = configuration.application.httpPort

    override fun filterPredicates() = listOf(erIkkeFerdigBehandletJournalpost)

    override fun onPacket(packet: Packet): Packet {
        logger.info { "Processing: $packet" }

        if (packet.getReadCount() >= 10) {
            logger.error { "Read count >= 10 for packet with journalpostid ${packet.getStringValue(PacketKeys.JOURNALPOST_ID)}" }
            throw ReadCountException()
        }

        return journalføringFerdigstill.handlePacket(packet)
    }

    override fun getConfig(): Properties {
        val properties = streamConfig(
            SERVICE_APP_ID,
            configuration.kafka.brokers,
            configuration.kafka.credential
        )
        properties[StreamsConfig.PROCESSING_GUARANTEE_CONFIG] = configuration.kafka.processingGuarantee
        return properties
    }

    override fun onFailure(packet: Packet, error: Throwable?): Packet {
        logger.error(error) { "Feilet ved håntering av journalpost: ${packet.getStringValue(PacketKeys.JOURNALPOST_ID)}. Pakke $packet" }
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

    val journalFøringFerdigstill = JournalføringFerdigstill(
        JournalpostRestApi(configuration.journalPostApiUrl, stsOidcClient),
        GosysOppgaveClient(configuration.gosysApiUrl, stsOidcClient),
        arenaClient
    )

    Application(configuration, journalFøringFerdigstill).start()
}

class ReadCountException : RuntimeException()