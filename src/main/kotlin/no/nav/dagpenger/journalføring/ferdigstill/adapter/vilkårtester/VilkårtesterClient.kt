package no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester

import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.moshi.responseObject
import no.nav.dagpenger.events.moshiInstance
import no.nav.dagpenger.journalføring.ferdigstill.adapter.Bruker
import java.time.LocalDate

interface Vilkårtester {
    fun hentSubsumsjonFor(aktørId: String): Subsumsjon
}

class VilkårtesterClient(private val regelApiUrl: String, private val regelApiKey: String) : Vilkårtester {
    private val jsonAdapter = moshiInstance.adapter(BehovRequest::class.java)

    override fun hentSubsumsjonFor(aktørId: String): Subsumsjon {
        val behovId = startBehov(aktørId)
        return Subsumsjon("", null, null, null, null)
    }

    private fun startBehov(aktørId: String): String {
        val behovUrl = "$regelApiUrl/behov"
        val json = jsonAdapter.toJson(createBehovRequest(aktørId))

        val (_, response, result) =
            with(
                behovUrl.httpPost()
                    .apiKey(regelApiKey)
                    .header(mapOf("Content-Type" to "application/json"))
                    .body(json)
            ) {
                responseObject<BehovStatusResponse>()
            }
        return result.fold({
            response.headers["Location"].first()

        }, { fuelError ->
            throw RegelApiBehovHttpClientException(
                "Failed to run behov. Response message ${response.responseMessage}. Error message: ${fuelError.message}"
            )

        })
    }
}

internal fun Request.apiKey(apiKey: String) = this.header("X-API-KEY", apiKey)

class RegelApiBehovHttpClientException(override val message: String) : RuntimeException(message)

private fun createBehovRequest(aktørId: String): BehovRequest {
    return BehovRequest(aktørId, -12345, LocalDate.now())
}