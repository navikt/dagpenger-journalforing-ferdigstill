package no.nav.dagpenger.journalføring.ferdigstill.adapter

import com.github.kittinunf.result.Result
import java.time.ZonedDateTime
import no.nav.dagpenger.journalføring.ferdigstill.ArenaIdParRespons
import no.nav.dagpenger.streams.HealthCheck

interface ArenaClient : HealthCheck {
    fun bestillOppgave(command: OppgaveCommand): Result<ArenaIdParRespons, Exception>
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
