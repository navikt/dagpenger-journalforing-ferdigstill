package no.nav.dagpenger.journalføring.ferdigstill

import mu.KotlinLogging
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.JOURNALPOST_ID
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.TOGGLE_BEHANDLE_NY_SØKNAD
import no.nav.dagpenger.oidc.StsOidcClient
import no.nav.dagpenger.streams.Pond
import no.nav.dagpenger.streams.streamConfig
import org.apache.kafka.streams.StreamsConfig
import java.util.Properties

private val logger = KotlinLogging.logger {}
internal const val JOURNALFØRING_FEATURE_TOGGLE_NAME = "dp-journalforing.ferdigstill.isEnabled"

internal class Application(
    private val configuration: Configuration,
    private val journalFøringFerdigstill: JournalFøringFerdigstill
) : Pond(configuration.kafka.dagpengerJournalpostTopic) {

    override val SERVICE_APP_ID = configuration.application.name
    override val HTTP_PORT: Int = configuration.application.httpPort

    private fun isEnabled(packet: Packet): Boolean = packet.hasField(TOGGLE_BEHANDLE_NY_SØKNAD) && packet.getBoolean(TOGGLE_BEHANDLE_NY_SØKNAD)

    override fun filterPredicates() = listOf(isJournalFørt)

    override fun onPacket(packet: Packet) {
        logger.info { "Skipping (quick fix): ${packet.getStringValue(JOURNALPOST_ID)}" }
        /*
        if (isEnabled(packet)) {
            logger.info { "Processing: $packet" }.also { journalFøringFerdigstill.handlePacket(packet) }
        } else {
            logger.info { "Skipping(due to feature toggle) : ${packet.getStringValue(JOURNALPOST_ID)}" }
        }
         */
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
}

fun main() {
    val configuration = Configuration()
    val stsOidcClient = StsOidcClient(configuration.sts.baseUrl, configuration.sts.username, configuration.sts.password)
    val journalFøringFerdigstill = JournalFøringFerdigstill(
        JournalPostRestApi(configuration.journalPostApiUrl, stsOidcClient),
        GosysOppgaveClient(configuration.gosysApiUrl, stsOidcClient))

    Application(configuration, journalFøringFerdigstill).start()
}
