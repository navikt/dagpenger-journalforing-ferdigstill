package no.nav.dagpenger.journalføring.opprydder

import mu.KotlinLogging
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.Configuration
import no.nav.dagpenger.journalføring.ferdigstill.ManuellJournalføringsBehandlingsChain
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys
import no.nav.dagpenger.journalføring.ferdigstill.adapter.GosysOppgaveClient
import no.nav.dagpenger.journalføring.ferdigstill.erIkkeFerdigBehandletJournalpost
import no.nav.dagpenger.journalføring.ferdigstill.skalFikses
import no.nav.dagpenger.streams.Pond
import no.nav.dagpenger.streams.streamConfig
import org.apache.kafka.streams.StreamsConfig
import org.apache.logging.log4j.ThreadContext
import java.util.Properties

private val logger = KotlinLogging.logger {}

internal class OpprydderApp(
    private val configuration: Configuration,
    private val gosysOppgaveClient: GosysOppgaveClient
) : Pond(configuration.kafka.dagpengerJournalpostTopic) {

    override val SERVICE_APP_ID = "dp-opprydder-manuell-behandling-test-run"
    override val HTTP_PORT: Int = 8079
    override val withHealthChecks: Boolean
        get() = false

    val manuellOppgaveChain = ManuellJournalføringsBehandlingsChain(gosysOppgaveClient, null)

    override fun filterPredicates() = listOf(erIkkeFerdigBehandletJournalpost, skalFikses)

    override fun onPacket(packet: Packet) {
        try {
            ThreadContext.put(
                "x_journalpost_id", packet.getStringValue(PacketKeys.JOURNALPOST_ID)
            )
            logger.info { "Opprydder bestiller manuell oppgave for: $packet" }
            manuellOppgaveChain.håndter(packet)
        } finally {
            ThreadContext.remove("x_journalpost_id")
        }
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