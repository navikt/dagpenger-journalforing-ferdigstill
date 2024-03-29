package no.nav.dagpenger.journalføring.ferdigstill

import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.JournalpostApi
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ManuellJournalføringsOppgaveClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester.Vilkårtester

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
    val klageAnkeLonnskompensasjonChain = KlageAnkeLonnskompensasjonChain(manuellJournalføringsOppgaveClient, manuellJournalføringsBehandlingsChain)
    val ferdigstillOppgaveChain = FerdigstillJournalpostBehandlingsChain(journalPostApi, klageAnkeLonnskompensasjonChain)
    val oppdaterChain = OppdaterJournalpostBehandlingsChain(journalPostApi, ferdigstillOppgaveChain)
    val eksisterendeSakChain = EksisterendeSaksForholdBehandlingsChain(arenaClient, oppdaterChain)
    val nySakChain = NyttSaksforholdBehandlingsChain(arenaClient, eksisterendeSakChain)
    val vilkårtestingChain = OppfyllerMinsteinntektBehandlingsChain(vilkårtester, nySakChain)
    val fornyetrettighet = FornyetRettighetBehandlingsChain(arenaClient, vilkårtestingChain)

    fun handlePacket(packet: Packet): Packet {
        try {
            return fornyetrettighet.håndter(packet)
        } catch (e: AdapterException) {
        }
        return packet
    }
}

class AdapterException(val exception: Throwable) : RuntimeException(exception)
