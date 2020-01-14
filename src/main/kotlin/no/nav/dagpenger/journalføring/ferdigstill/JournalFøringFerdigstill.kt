package no.nav.dagpenger.journalføring.ferdigstill

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import mu.KotlinLogging
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.events.moshiInstance
import no.nav.dagpenger.journalføring.ferdigstill.Metrics.aktiveDagpengeSakTeller
import no.nav.dagpenger.journalføring.ferdigstill.Metrics.automatiskJournalførtNeiTeller
import no.nav.dagpenger.journalføring.ferdigstill.Metrics.avsluttetDagpengeSakTeller
import no.nav.dagpenger.journalføring.ferdigstill.Metrics.inaktivDagpengeSakTeller
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.AKTØR_ID
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.BEHANDLENDE_ENHET
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.NATURLIG_IDENT
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.brukerFrom
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.dokumentTitlerFrom
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.hasNaturligIdent
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.journalPostFrom
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.journalPostIdFrom
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.nullableAktørFrom
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.registrertDatoFrom
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.tildeltEnhetsNrFrom
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.tittelFrom
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaSak
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaSakStatus
import no.nav.dagpenger.journalføring.ferdigstill.adapter.BestillOppgaveArenaException
import no.nav.dagpenger.journalføring.ferdigstill.adapter.createArenaTilleggsinformasjon
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.BestillOppgavePersonErInaktiv
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.BestillOppgavePersonIkkeFunnet
import org.apache.kafka.streams.kstream.Predicate

private val logger = KotlinLogging.logger {}

internal val erJournalpost = Predicate<String, Packet> { _, packet ->
    packet.hasField(PacketKeys.JOURNALPOST_ID)
}

internal val dokumentAdapter = moshiInstance.adapter<List<Dokument>>(
    Types.newParameterizedType(
        List::class.java,
        Dokument::class.java
    )
)

internal object PacketToJoarkPayloadMapper {
    val dokumentJsonAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build().adapter<List<Dokument>>(
            Types.newParameterizedType(
                List::class.java,
                Dokument::class.java
            )
        ).lenient()

    fun journalPostIdFrom(packet: Packet) = packet.getStringValue(PacketKeys.JOURNALPOST_ID)
    fun avsenderFrom(packet: Packet) = Avsender(packet.getStringValue(PacketKeys.AVSENDER_NAVN))
    fun brukerFrom(packet: Packet) = Bruker(packet.getStringValue(NATURLIG_IDENT))
    fun hasNaturligIdent(packet: Packet) = packet.hasField(NATURLIG_IDENT)
    fun nullableAktørFrom(packet: Packet) =
        if (packet.hasField(AKTØR_ID)) Bruker(packet.getStringValue(AKTØR_ID), "AKTØR") else null

    fun tildeltEnhetsNrFrom(packet: Packet) = packet.getStringValue(BEHANDLENDE_ENHET)
    fun dokumenterFrom(packet: Packet) = packet.getObjectValue(PacketKeys.DOKUMENTER) {
        dokumentJsonAdapter.fromJsonValue(it)!!
    }

    fun registrertDatoFrom(packet: Packet) = packet.getStringValue(PacketKeys.DATO_REGISTRERT)
    fun dokumentTitlerFrom(packet: Packet) =
        packet.getObjectValue(PacketKeys.DOKUMENTER) { dokumentAdapter.fromJsonValue(it)!! }.map { it.tittel }

    fun tittelFrom(packet: Packet) = dokumenterFrom(packet).first().tittel

    fun sakFrom(fagsakId: String?) = if (fagsakId != null) Sak(
        saksType = SaksType.FAGSAK,
        fagsaksystem = "AO01",
        fagsakId = fagsakId
    ) else Sak(SaksType.GENERELL_SAK, null, null)

    fun journalPostFrom(packet: Packet, fagsakId: String?): OppdaterJournalPostPayload {
        return OppdaterJournalPostPayload(
            avsenderMottaker = avsenderFrom(packet),
            bruker = brukerFrom(packet),
            tittel = tittelFrom(packet),
            sak = sakFrom(fagsakId),
            dokumenter = dokumenterFrom(packet)
        )
    }
}

internal class JournalFøringFerdigstill(
    private val journalPostApi: JournalPostApi,
    private val manuellJournalføringsOppgaveClient: ManuellJournalføringsOppgaveClient,
    private val arenaClient: ArenaClient
) {

    fun handlePacket(packet: Packet) {

        if (kanBestilleFagsak(packet)) {
            val fagsakId = bestillFagsak(packet)
            if (fagsakId != null) {
                journalførAutomatisk(packet, fagsakId)
            } else {
                journalførManuelt(packet)
            }
        } else {
            journalførManuelt(packet)
        }
    }

    private fun journalførAutomatisk(packet: Packet, fagsakId: String) {
        val journalpostId = journalPostIdFrom(packet)
        journalPostApi.oppdater(journalpostId, journalPostFrom(packet, fagsakId))
        journalPostApi.ferdigstill(journalpostId)
        Metrics.jpFerdigStillInc(SaksType.FAGSAK)
        logger.info { "Automatisk journalført $journalpostId" }
    }

    private fun journalførManuelt(packet: Packet) {
        val journalpostId = journalPostIdFrom(packet)

        nullableAktørFrom(packet)?.let { journalPostApi.oppdater(journalpostId, journalPostFrom(packet, null)) }

        manuellJournalføringsOppgaveClient.opprettOppgave(
            journalpostId,
            nullableAktørFrom(packet)?.id,
            tittelFrom(packet),
            tildeltEnhetsNrFrom(packet)
        )

        Metrics.jpFerdigStillInc(SaksType.GENERELL_SAK)
        logger.info { "Manuelt journalført $journalpostId" }
    }

    private fun kanBestilleFagsak(packet: Packet): Boolean {
        if (!hasNaturligIdent(packet)) {
            automatiskJournalførtNeiTeller("ukjent_bruker")
            return false
        }

        val saker = arenaClient.hentArenaSaker(brukerFrom(packet).id).also {
            registrerMetrikker(it)
            logger.info {
                "Innsender av journalpost ${journalPostIdFrom(packet)} har ${it.filter { it.status == ArenaSakStatus.Aktiv }.size} aktive saker av ${it.size} dagpengesaker totalt"
            }
        }

        if (saker.any { it.status == ArenaSakStatus.Aktiv }) {
            automatiskJournalførtNeiTeller("aktiv_sak")
            return false
        }

        return true
    }

    private fun registrerMetrikker(saker: List<ArenaSak>) {
        saker.filter { it.status == ArenaSakStatus.Aktiv }.also { aktiveDagpengeSakTeller.inc(it.size.toDouble()) }
        saker.filter { it.status == ArenaSakStatus.Lukket }.also { avsluttetDagpengeSakTeller.inc(it.size.toDouble()) }
        saker.filter { it.status == ArenaSakStatus.Inaktiv }.also { inaktivDagpengeSakTeller.inc(it.size.toDouble()) }
    }

    private fun bestillFagsak(packet: Packet): String? {
        val journalpostId = journalPostIdFrom(packet)
        val tilleggsinformasjon =
            createArenaTilleggsinformasjon(dokumentTitlerFrom(packet), registrertDatoFrom(packet))
        return try {
            arenaClient.bestillOppgave(brukerFrom(packet).id, tildeltEnhetsNrFrom(packet), tilleggsinformasjon)
        } catch (e: BestillOppgaveArenaException) {
            automatiskJournalførtNeiTeller(e.cause?.javaClass?.simpleName ?: "ukjent")

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
                    throw e
                }
            }
        }
    }
}

class MåManueltBehandlesException : RuntimeException()