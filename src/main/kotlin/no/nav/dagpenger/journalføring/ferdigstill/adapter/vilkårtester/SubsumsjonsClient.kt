package no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester

import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.moshi.moshiDeserializerOf
import no.nav.dagpenger.events.moshiInstance

class SubsumsjonsClient(private val regelApiUrl: String, private val regelApiKey: String) {
    fun getSubsumsjon(subsumsjonLocation: String): Subsumsjon {
        val url = "$regelApiUrl$subsumsjonLocation"

        val jsonAdapter = moshiInstance.adapter(Subsumsjon::class.java)

        val (_, response, result) =
            with(
                url
                    .httpGet()
                    .apiKey(regelApiKey)
            ) { responseObject(moshiDeserializerOf(jsonAdapter)) }

        return result.fold(
            {
                it
            },
            { error ->
                throw RegelApiSubsumsjonHttpClientException(
                    "Failed to fetch subsumsjon. Response message: ${response.responseMessage}. Error message: ${error.message}"
                )
            }
        )
    }
}

class RegelApiSubsumsjonHttpClientException(override val message: String) : RuntimeException(message)
