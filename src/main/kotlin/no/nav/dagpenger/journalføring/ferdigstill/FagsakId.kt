package no.nav.dagpenger.journalføring.ferdigstill

data class FagsakId(val value: String)
data class OppgaveId(val value: String)

data class IdPar(
    val oppgaveId: OppgaveId,
    val fagsakId: FagsakId? = null
)