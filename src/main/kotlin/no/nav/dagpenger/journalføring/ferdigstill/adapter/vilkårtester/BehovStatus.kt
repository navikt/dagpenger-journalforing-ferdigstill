package no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester

enum class BehovStatus {
    PENDING
}

data class BehovStatusResponse(val status: BehovStatus)