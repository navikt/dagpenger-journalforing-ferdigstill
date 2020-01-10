package no.nav.dagpenger.journalføring.arena.adapter

import no.nav.dagpenger.streams.HealthCheck

interface ArenaClient : HealthCheck {
    fun bestillOppgave(naturligIdent: String, behandlendeEnhetId: String, tilleggsinformasjon: String): String
    fun hentArenaSaker(naturligIdent: String): List<ArenaSak>
}
