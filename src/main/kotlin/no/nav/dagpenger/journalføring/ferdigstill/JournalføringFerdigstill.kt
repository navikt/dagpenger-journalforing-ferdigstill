package no.nav.dagpenger.journalføring.ferdigstill

import com.squareup.moshi.Types
import mu.KotlinLogging
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.events.moshiInstance
import no.nav.dagpenger.journalføring.ferdigstill.Metrics.automatiskJournalførtNeiTellerInc
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.brukerFrom
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.dokumentTitlerFrom
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.hasNaturligIdent
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.journalPostFrom
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.journalpostIdFrom
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.nullableAktørFrom
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.registrertDatoFrom
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.tildeltEnhetsNrFrom
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.tittelFrom
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaSakStatus
import no.nav.dagpenger.journalføring.ferdigstill.adapter.Avsender
import no.nav.dagpenger.journalføring.ferdigstill.adapter.BestillOppgaveArenaException
import no.nav.dagpenger.journalføring.ferdigstill.adapter.Bruker
import no.nav.dagpenger.journalføring.ferdigstill.adapter.Dokument
import no.nav.dagpenger.journalføring.ferdigstill.adapter.JournalpostApi
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ManuellJournalføringsOppgaveClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.OppdaterJournalpostPayload
import no.nav.dagpenger.journalføring.ferdigstill.adapter.OppgaveCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.Sak
import no.nav.dagpenger.journalføring.ferdigstill.adapter.SaksType
import no.nav.dagpenger.journalføring.ferdigstill.adapter.StartVedtakCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.VurderGjenopptakCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.createArenaTilleggsinformasjon
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.BestillOppgavePersonErInaktiv
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.BestillOppgavePersonIkkeFunnet
import org.apache.kafka.streams.kstream.Predicate

private val logger = KotlinLogging.logger {}

internal val erIkkeFerdigBehandletJournalpost = Predicate<String, Packet> { _, packet ->
    packet.hasField(PacketKeys.JOURNALPOST_ID) &&
        !packet.hasField(PacketKeys.FERDIG_BEHANDLET)
}

internal object PacketToJoarkPayloadMapper {
    val dokumentJsonAdapter = moshiInstance.adapter<List<Dokument>>(
            Types.newParameterizedType(
                List::class.java,
                Dokument::class.java
            )
        ).lenient()

    fun journalpostIdFrom(packet: Packet) = packet.getStringValue(PacketKeys.JOURNALPOST_ID)
    fun avsenderFrom(packet: Packet) =
        Avsender(packet.getStringValue(PacketKeys.AVSENDER_NAVN))
    fun brukerFrom(packet: Packet) =
        Bruker(packet.getStringValue(PacketKeys.NATURLIG_IDENT))
    fun hasNaturligIdent(packet: Packet) = packet.hasField(PacketKeys.NATURLIG_IDENT)
    fun nullableAktørFrom(packet: Packet) =
        if (packet.hasField(PacketKeys.AKTØR_ID)) Bruker(
            packet.getStringValue(PacketKeys.AKTØR_ID),
            "AKTØR"
        ) else null

    fun tildeltEnhetsNrFrom(packet: Packet) = packet.getStringValue(PacketKeys.BEHANDLENDE_ENHET)
    fun dokumenterFrom(packet: Packet) = packet.getObjectValue(PacketKeys.DOKUMENTER) {
        dokumentJsonAdapter.fromJsonValue(it)!!
    }

    fun registrertDatoFrom(packet: Packet) = packet.getStringValue(PacketKeys.DATO_REGISTRERT)
    fun dokumentTitlerFrom(packet: Packet) =
        packet.getObjectValue(PacketKeys.DOKUMENTER) { dokumentJsonAdapter.fromJsonValue(it)!! }.map { it.tittel }

    fun tittelFrom(packet: Packet) = dokumenterFrom(packet).first().tittel

    fun sakFrom(fagsakId: FagsakId?) = if (fagsakId != null) Sak(
        saksType = SaksType.FAGSAK,
        fagsaksystem = "AO01",
        fagsakId = fagsakId.value
    ) else Sak(
        SaksType.GENERELL_SAK,
        null,
        null
    )

    fun journalPostFrom(packet: Packet, fagsakId: FagsakId?): OppdaterJournalpostPayload {
        return OppdaterJournalpostPayload(
            avsenderMottaker = avsenderFrom(packet),
            bruker = brukerFrom(packet),
            tittel = tittelFrom(packet),
            sak = sakFrom(fagsakId),
            dokumenter = dokumenterFrom(packet)
        )
    }
}

internal class JournalføringFerdigstill(
    private val journalPostApi: JournalpostApi,
    private val manuellJournalføringsOppgaveClient: ManuellJournalføringsOppgaveClient,
    private val arenaClient: ArenaClient
) {

    fun handlePacket(packet: Packet): Packet {

        if (!hasNaturligIdent(packet)) {
            automatiskJournalførtNeiTellerInc("ukjent_bruker")
            journalførManuelt(packet)
            packet.putValue(PacketKeys.FERDIG_BEHANDLET, true)
            return packet
        }

        return when (packet.getStringValue("henvendelsestype")) {
            "NY_SØKNAD" -> behandleNySøknad(packet)
            "GJENOPPTAK" -> behandleGjenopptak(packet)
            else -> throw NotImplementedError()
        }
    }

    private fun behandleGjenopptak(packet: Packet): Packet {
        try {
            val tilleggsinformasjon =
                createArenaTilleggsinformasjon(dokumentTitlerFrom(packet), registrertDatoFrom(packet))

            bestillOppgave(VurderGjenopptakCommand(
                naturligIdent = brukerFrom(packet).id,
                behandlendeEnhetId = tildeltEnhetsNrFrom(packet),
                tilleggsinformasjon = tilleggsinformasjon
            ), journalpostIdFrom(packet))

            journalførAutomatisk(packet)
            packet.putValue(PacketKeys.FERDIG_BEHANDLET, true)
        } catch (e: AdapterException) {
        }

        return packet
    }

    fun behandleNySøknad(packet: Packet): Packet {
        try {
            if (kanBestilleFagsak(packet)) {
                val tilleggsinformasjon =
                    createArenaTilleggsinformasjon(dokumentTitlerFrom(packet), registrertDatoFrom(packet))

                val fagsakId =
                    packet.getNullableStringValue(PacketKeys.FAGSAK_ID)?.let { FagsakId(it) }
                        ?: bestillOppgave(
                            StartVedtakCommand(
                                naturligIdent = brukerFrom(packet).id,
                                behandlendeEnhetId = tildeltEnhetsNrFrom(packet),
                                tilleggsinformasjon = tilleggsinformasjon
                            ),
                            journalpostIdFrom(packet)
                        )

                if (fagsakId != null) {
                    if (!packet.hasField(PacketKeys.FAGSAK_ID)) packet.putValue(PacketKeys.FAGSAK_ID, fagsakId.value)
                    journalførAutomatisk(packet, fagsakId)
                    packet.putValue(PacketKeys.FERDIG_BEHANDLET, true)
                } else {
                    journalførManuelt(packet)
                    packet.putValue(PacketKeys.FERDIG_BEHANDLET, true)
                }
            } else {
                journalførManuelt(packet)
                packet.putValue(PacketKeys.FERDIG_BEHANDLET, true)
            }
        } catch (e: AdapterException) {
        }

        return packet
    }

    private fun journalførAutomatisk(packet: Packet, fagsakId: FagsakId? = null) {
        val journalpostId = journalpostIdFrom(packet)
        journalPostApi.oppdater(journalpostId, journalPostFrom(packet, fagsakId))
        journalPostApi.ferdigstill(journalpostId)
        Metrics.jpFerdigStillInc()
        Metrics.automatiskJournalførtJaTellerInc()
        logger.info { "Automatisk journalført $journalpostId" }
    }

    private fun journalførManuelt(packet: Packet) {
        val journalpostId = journalpostIdFrom(packet)

        nullableAktørFrom(packet)?.let { journalPostApi.oppdater(journalpostId, journalPostFrom(packet, null)) }

        manuellJournalføringsOppgaveClient.opprettOppgave(
            journalpostId,
            nullableAktørFrom(packet)?.id,
            tittelFrom(packet),
            tildeltEnhetsNrFrom(packet)
        )

        Metrics.jpFerdigStillInc()
        logger.info { "Manuelt journalført $journalpostId" }
    }

    private fun kanBestilleFagsak(packet: Packet): Boolean {
        val saker = arenaClient.hentArenaSaker(brukerFrom(packet).id).also {
            logger.info {
                "Innsender av journalpost ${journalpostIdFrom(packet)} har ${it.filter { it.status == ArenaSakStatus.Aktiv }.size} aktive saker av ${it.size} dagpengesaker totalt"
            }
        }

        return saker.none { it.status == ArenaSakStatus.Aktiv }
            .also { if (!it) automatiskJournalførtNeiTellerInc("aktiv_sak") }
    }

    private fun bestillOppgave(command: OppgaveCommand, journalpostId: String): FagsakId? {
        return try {
            arenaClient.bestillOppgave(command)
        } catch (e: BestillOppgaveArenaException) {
            automatiskJournalførtNeiTellerInc(e.cause?.javaClass?.simpleName ?: "ukjent")

            return when (e.cause) {
                is BestillOppgavePersonErInaktiv -> {
                    logger.warn { "Kan ikke bestille oppgave for journalpost $journalpostId. Person ikke arbeidssøker " }
                    null
                }
                is BestillOppgavePersonIkkeFunnet -> {
                    logger.warn { "Kan ikke bestille oppgave for journalpost $journalpostId. Person ikke funnet i arena " }
                    null
                }
                else -> {
                    logger.warn { "Kan ikke bestille oppgave for journalpost $journalpostId. Ukjent feil. " }
                    throw AdapterException(e)
                }
            }
        }
    }
}

class AdapterException(val exception: Throwable) : RuntimeException(exception)