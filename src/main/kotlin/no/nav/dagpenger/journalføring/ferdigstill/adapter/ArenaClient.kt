package no.nav.dagpenger.journalføring.ferdigstill.adapter

import no.nav.dagpenger.journalføring.ferdigstill.FagsakId
import no.nav.dagpenger.streams.HealthCheck

interface ArenaClient : HealthCheck {
    fun bestillOppgave(command: OppgaveCommand): FagsakId?
    fun hentArenaSaker(naturligIdent: String): List<ArenaSak>
}

sealed class OppgaveCommand(val naturligIdent: String, val behandlendeEnhet: String, val informasjon: String)

class LagOppgaveOgSakCommand(naturligIdent: String, behandlendeEnhetId: String, tilleggsinformasjon: String) : OppgaveCommand(naturligIdent, behandlendeEnhetId, tilleggsinformasjon)
class LagOppgaveCommand(naturligIdent: String, behandlendeEnhetId: String, tilleggsinformasjon: String) : OppgaveCommand(naturligIdent, behandlendeEnhetId, tilleggsinformasjon)
