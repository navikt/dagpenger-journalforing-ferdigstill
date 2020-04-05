package no.nav.dagpenger.journalføring.ferdigstill

data class FagsakId(val value: String)
data class OppgaveId(val value: String)

data class ArenaIdParRespons(
    val oppgaveId: OppgaveId,
    val fagsakId: FagsakId? = null
)