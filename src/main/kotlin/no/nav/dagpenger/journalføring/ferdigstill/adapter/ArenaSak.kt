package no.nav.dagpenger.journalføring.ferdigstill.adapter

data class ArenaSakId(val id: String)

data class ArenaSak(val fagsystemSakId: Int, val status: ArenaSakStatus)

enum class ArenaSakStatus {
    Aktiv, Inaktiv, Lukket
}
