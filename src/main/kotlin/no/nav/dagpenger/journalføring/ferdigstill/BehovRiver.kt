package no.nav.dagpenger.journalføring.ferdigstill

import no.nav.helse.rapids_rivers.JsonMessage

abstract class BehovRiver {
    internal fun opprettBehov(behov: MutableMap<String, String>) {
        TODO("Not yet implemented")
    }

    internal fun hentSvar(id: Unit): JsonMessage {
        TODO("Not yet implemented")
    }
}
