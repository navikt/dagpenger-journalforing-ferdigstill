package no.nav.dagpenger.journalføring.ferdigstill.adapter

import mu.KotlinLogging
import no.nav.dagpenger.journalføring.ferdigstill.AdapterException
import no.nav.dagpenger.journalføring.ferdigstill.FagsakId
import no.nav.dagpenger.journalføring.ferdigstill.Metrics
import no.nav.dagpenger.streams.HealthCheck
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.BestillOppgavePersonErInaktiv
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.BestillOppgavePersonIkkeFunnet
import java.time.ZonedDateTime



interface ArenaClient : HealthCheck {
    fun bestillOppgave(command: OppgaveCommand): FagsakId?
    fun harIkkeAktivSak(bruker: Bruker): Boolean
}

sealed class OppgaveCommand {
    abstract val naturligIdent: String
    abstract val behandlendeEnhetId: String
    abstract val tilleggsinformasjon: String
    abstract val registrertDato: ZonedDateTime
    abstract val oppgavebeskrivelse: String
}

class StartVedtakCommand(
    override val naturligIdent: String,
    override val behandlendeEnhetId: String,
    override val tilleggsinformasjon: String,
    override val registrertDato: ZonedDateTime,
    override val oppgavebeskrivelse: String

) : OppgaveCommand()

class VurderHenvendelseAngåendeEksisterendeSaksforholdCommand(
    override val naturligIdent: String,
    override val behandlendeEnhetId: String,
    override val tilleggsinformasjon: String,
    override val registrertDato: ZonedDateTime,
    override val oppgavebeskrivelse: String
) : OppgaveCommand()
