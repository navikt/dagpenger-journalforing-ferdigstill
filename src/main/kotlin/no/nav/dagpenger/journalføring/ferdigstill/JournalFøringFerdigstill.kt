package no.nav.dagpenger.journalføring.ferdigstill

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.events.moshiInstance
import no.nav.dagpenger.journalføring.arena.adapter.ArenaClient
import no.nav.dagpenger.journalføring.arena.adapter.ArenaSakStatus
import no.nav.dagpenger.journalføring.arena.adapter.createArenaTilleggsinformasjon
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.AKTØR_ID
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.BEHANDLENDE_ENHET
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.NATURLIG_IDENT
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.aktørFrom
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.brukerFrom
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.dokumentTitlerFrom
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.hasNaturligIdent
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.journalPostFrom
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.journalPostIdFrom
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.registrertDatoFrom
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.sakFrom
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.tildeltEnhetsNrFrom
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.tittelFrom
import org.apache.kafka.streams.kstream.Predicate

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
    fun aktørFrom(packet: Packet) = Bruker(packet.getStringValue(AKTØR_ID), "AKTØR")
    fun tildeltEnhetsNrFrom(packet: Packet) = packet.getStringValue(BEHANDLENDE_ENHET)
    fun dokumenterFrom(packet: Packet) = packet.getObjectValue(PacketKeys.DOKUMENTER) {
        dokumentJsonAdapter.fromJsonValue(it)!!
    }
    fun registrertDatoFrom(packet: Packet) = packet.getStringValue(PacketKeys.DATO_REGISTRERT)
    fun dokumentTitlerFrom(packet: Packet) =
        packet.getObjectValue(PacketKeys.DOKUMENTER) { dokumentAdapter.fromJsonValue(it)!! }.map { it.tittel }

    fun tittelFrom(packet: Packet) = dokumenterFrom(packet).first().tittel
    fun sakFrom(packet: Packet) = when (packet.hasField(PacketKeys.ARENA_SAK_ID)) {
        true -> Sak(
            saksType = SaksType.FAGSAK,
            fagsaksystem = "AO01",
            fagsakId = packet.getStringValue(PacketKeys.ARENA_SAK_ID))
        else -> Sak(SaksType.GENERELL_SAK, null, null)
    }

    fun journalPostFrom(packet: Packet): OppdaterJournalPostPayload {
        return OppdaterJournalPostPayload(
            avsenderMottaker = avsenderFrom(packet),
            bruker = brukerFrom(packet),
            tittel = tittelFrom(packet),
            sak = sakFrom(packet),
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
        val saksType = sakFrom(packet).saksType
        val journalpostId = journalPostIdFrom(packet)

        if (!hasNaturligIdent(packet)) {
            manuellJournalføringsOppgaveClient.opprettOppgave(journalpostId, null, tittelFrom(packet), tildeltEnhetsNrFrom(packet))
            return
        }

        val arenasaker = arenaClient.hentArenaSaker(brukerFrom(packet).id)
        if (arenasaker.none { it.status == ArenaSakStatus.Aktiv }) {
            val tilleggsinformasjon = createArenaTilleggsinformasjon(dokumentTitlerFrom(packet), registrertDatoFrom(packet))
            arenaClient.bestillOppgave(brukerFrom(packet).id, tildeltEnhetsNrFrom(packet), tilleggsinformasjon)
        } else {
            val aktørId = if (hasNaturligIdent(packet)) aktørFrom(packet).id else null
            manuellJournalføringsOppgaveClient.opprettOppgave(journalpostId, aktørId, tittelFrom(packet), tildeltEnhetsNrFrom(packet))
            return
        }

        journalPostApi.oppdater(journalpostId, journalPostFrom(packet))
        journalPostApi.ferdigstill(journalpostId)

        Metrics.jpFerdigStillInc(saksType)
    }

}
