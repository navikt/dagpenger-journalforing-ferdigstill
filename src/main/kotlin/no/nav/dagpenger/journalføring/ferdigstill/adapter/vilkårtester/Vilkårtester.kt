package no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester

import kotlinx.coroutines.runBlocking

interface MinsteArbeidsinntektVilkår {
    fun harBestått(aktørId: String): Boolean?
}

class Vilkårtester(regelApiUrl: String, regelApiKey: String) : MinsteArbeidsinntektVilkår {
    private val BehovClient = BehovClient(regelApiUrl = regelApiUrl, regelApiKey = regelApiKey)
    private val statusPollClient = BehovStatusPoller(regelApiUrl = regelApiUrl, regelApiKey = regelApiKey)
    private val subsumsjonsClient = SubsumsjonsClient(regelApiUrl = regelApiUrl, regelApiKey = regelApiKey)

    private fun hentSubsumsjonFor(aktørId: String): Subsumsjon {
        val statusUrl = BehovClient.startBehov(aktørId)
        val subsumsjonsLocation = runBlocking { statusPollClient.pollStatus(statusUrl) }
        val subsumsjon = subsumsjonsClient.getSubsumsjon(subsumsjonsLocation)

        return subsumsjon
    }

    override fun harBestått(aktørId: String) =
        hentSubsumsjonFor(aktørId).minsteinntektResultat?.oppfyllerMinsteinntekt
}
