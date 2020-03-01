package no.nav.dagpenger.journalføring.ferdigstill

import com.squareup.moshi.Types
import mu.KotlinLogging
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.events.moshiInstance
import no.nav.dagpenger.journalføring.ferdigstill.PacketMapper.bruker
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.Avsender
import no.nav.dagpenger.journalføring.ferdigstill.adapter.Bruker
import no.nav.dagpenger.journalføring.ferdigstill.adapter.Dokument
import no.nav.dagpenger.journalføring.ferdigstill.adapter.JournalpostApi
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ManuellJournalføringsOppgaveClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.OppdaterJournalpostPayload
import no.nav.dagpenger.journalføring.ferdigstill.adapter.Sak
import no.nav.dagpenger.journalføring.ferdigstill.adapter.SaksType
import org.apache.kafka.streams.kstream.Predicate
import java.time.LocalDateTime
import java.time.ZoneId

private val logger = KotlinLogging.logger {}

internal val erIkkeFerdigBehandletJournalpost = Predicate<String, Packet> { _, packet ->
    packet.hasField(PacketKeys.JOURNALPOST_ID) &&
        !packet.hasField(PacketKeys.FERDIG_BEHANDLET)
}

internal object PacketMapper {
    val dokumentJsonAdapter = moshiInstance.adapter<List<Dokument>>(
        Types.newParameterizedType(
            List::class.java,
            Dokument::class.java
        )
    ).lenient()

    fun journalpostIdFrom(packet: Packet) = packet.getStringValue(PacketKeys.JOURNALPOST_ID)
    fun avsenderFrom(packet: Packet) =
        Avsender(packet.getStringValue(PacketKeys.AVSENDER_NAVN))

    fun bruker(packet: Packet) =
        Bruker(packet.getStringValue(PacketKeys.NATURLIG_IDENT))

    fun hasNaturligIdent(packet: Packet) = packet.hasField(PacketKeys.NATURLIG_IDENT)
    fun nullableAktørFrom(packet: Packet) =
        if (packet.hasField(PacketKeys.AKTØR_ID)) Bruker(
            packet.getStringValue(PacketKeys.AKTØR_ID),
            "AKTØR"
        ) else null

    fun henvendelse(packet: Packet): Henvendelse = Henvendelse.fra(packet.getStringValue(PacketKeys.HENVENDELSESTYPE))
    fun harFagsakId(packet: Packet): Boolean = packet.hasField(PacketKeys.FAGSAK_ID)
    fun harIkkeFagsakId(packet: Packet): Boolean = !harFagsakId(packet)

    fun tildeltEnhetsNrFrom(packet: Packet) = packet.getStringValue(PacketKeys.BEHANDLENDE_ENHET)
    fun dokumenterFrom(packet: Packet) = packet.getObjectValue(PacketKeys.DOKUMENTER) {
        dokumentJsonAdapter.fromJsonValue(it)!!
    }

    fun registrertDatoFrom(packet: Packet) =
        LocalDateTime.parse(packet.getStringValue(PacketKeys.DATO_REGISTRERT)).atZone(ZoneId.of("Europe/Oslo"))

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
            bruker = bruker(packet),
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

    val ferdigBehandlingslenke = MarkerFerdigBehandlingslenke(null)
    val manuellOppgaveLenke =
        ManuellJournalføringsBehandlingslenke(manuellJournalføringsOppgaveClient, ferdigBehandlingslenke)
    val ferdigstillOppgaveLenke = FerdigstillJournalpostBehandlingslenke(journalPostApi, manuellOppgaveLenke)
    val oppdaterLenke = OppdaterJournalpostBehandlingslenke(journalPostApi, ferdigstillOppgaveLenke)
    val eksisterendeSakLenke = EksisterendeSaksForholdBehandlingslenke(arenaClient, oppdaterLenke)
    val nySakLenke = NyttSaksforholdBehandlingslenke(arenaClient, eksisterendeSakLenke)

    fun handlePacket(packet: Packet): Packet {
        try {
            return nySakLenke.håndter(packet)
        } catch (e: AdapterException) {
        }
        return packet
    }
}

class AdapterException(val exception: Throwable) : RuntimeException(exception)