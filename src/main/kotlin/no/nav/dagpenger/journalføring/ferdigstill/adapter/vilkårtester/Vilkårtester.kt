package no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester

import kotlinx.coroutines.runBlocking

class Vilkårtester(regelApiUrl: String, regelApiKey: String) {
    private val BehovClient = BehovClient(regelApiUrl = regelApiUrl, regelApiKey = regelApiKey)
    private val statusPollClient = BehovStatusPoller(regelApiUrl = regelApiUrl, regelApiKey = regelApiKey)
    private val subsumsjonsClient = SubsumsjonsClient(regelApiUrl = regelApiUrl, regelApiKey = regelApiKey)

    private fun hentSubsumsjonFor(aktørId: String): Subsumsjon {
        val statusUrl = BehovClient.startBehov(aktørId)
        val subsumsjonsLocation = runBlocking { statusPollClient.pollStatus(statusUrl) }
        val subsumsjon = subsumsjonsClient.getSubsumsjon(subsumsjonsLocation)

        return subsumsjon
    }

    fun hentMinsteArbeidsinntektVilkår(aktørId: String) =
        hentSubsumsjonFor(aktørId).minsteinntektResultat?.let {
            MinsteArbeidsinntektVilkår(
                it.oppfyllerMinsteinntekt,
                it.beregningsregel == Beregningsregel.KORONA
            )
        }
}

data class MinsteArbeidsinntektVilkår(
    val harBeståttMinsteArbeidsinntektVilkår: Boolean,
    val koronaRegelverkBrukt: Boolean
)
