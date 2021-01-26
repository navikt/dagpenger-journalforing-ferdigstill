package no.nav.dagpenger.journalføring.ferdigstill

import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.IgnoreJournalPost.ignorerJournalpost
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.JournalpostApi
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ManuellJournalføringsOppgaveClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester.Vilkårtester
import org.apache.kafka.streams.kstream.Predicate

internal val erIkkeFerdigBehandletJournalpost = Predicate<String, Packet> { _, packet ->
    packet.hasField(PacketKeys.JOURNALPOST_ID) &&
        !packet.hasField(PacketKeys.FERDIG_BEHANDLET) &&
        !ignorerJournalpost.contains(packet.getStringValue(PacketKeys.JOURNALPOST_ID))
}

internal class JournalføringFerdigstill(
    journalPostApi: JournalpostApi,
    manuellJournalføringsOppgaveClient: ManuellJournalføringsOppgaveClient,
    arenaClient: ArenaClient,
    vilkårtester: Vilkårtester
) {

    val statistikkChain = StatistikkChain(null)
    val ferdigBehandlingsChain = MarkerFerdigBehandlingsChain(statistikkChain)
    val manuellJournalføringsBehandlingsChain =
        ManuellJournalføringsBehandlingsChain(manuellJournalføringsOppgaveClient, ferdigBehandlingsChain)
    val ferdigstillOppgaveChain = FerdigstillJournalpostBehandlingsChain(journalPostApi, manuellJournalføringsBehandlingsChain)
    val oppdaterChain = OppdaterJournalpostBehandlingsChain(journalPostApi, ferdigstillOppgaveChain)
    val eksisterendeSakChain = EksisterendeSaksForholdBehandlingsChain(arenaClient, oppdaterChain)
    val nySakChain = NyttSaksforholdBehandlingsChain(arenaClient, eksisterendeSakChain)
    val vilkårtestingChain = OppfyllerMinsteinntektBehandlingsChain(vilkårtester, nySakChain)
    val fornyetrett = FornyetRettighetBehandlingsChain(arenaClient, vilkårtestingChain)

    fun handlePacket(packet: Packet): Packet {
        try {
            return fornyetrett.håndter(packet)
        } catch (e: AdapterException) {
        }
        return packet
    }
}

class AdapterException(val exception: Throwable) : RuntimeException(exception)
