package no.nav.dagpenger.journalføring.ferdigstill

interface OppslagClient {
    fun ferdigstillJournalføring(journalpostId: String)
}
