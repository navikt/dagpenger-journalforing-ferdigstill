package no.nav.dagpenger.journalføring.ferdigstill.adapter

import no.nav.dagpenger.journalføring.ferdigstill.FagsakId
import no.nav.dagpenger.streams.HealthCheck

interface ArenaClient : HealthCheck {
    fun bestillOppgave(command: OppgaveCommand): FagsakId?
    fun hentArenaSaker(naturligIdent: String): List<ArenaSak>
}

sealed class OppgaveCommand { abstract val naturligIdent: String; abstract val behandlendeEnhetId: String; abstract val tilleggsinformasjon: String }

class StartVedtakCommand(override val naturligIdent: String, override val behandlendeEnhetId: String, override val tilleggsinformasjon: String) : OppgaveCommand()
class VurderHenvendelseAngåendeEksisterendeSaksforholdCommand(override val naturligIdent: String, override val behandlendeEnhetId: String, override val tilleggsinformasjon: String, val oppgavebeskrivelse: String) : OppgaveCommand()
