package no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester

import com.github.kittinunf.fuel.httpPost
import no.nav.dagpenger.events.moshiInstance
import no.nav.dagpenger.journalføring.ferdigstill.adapter.responseObject
import java.time.LocalDate

class BehovClient(private val regelApiUrl: String, private val regelApiKey: String) {
    private val jsonAdapter = moshiInstance.adapter(BehovRequest::class.java)

    fun startBehov(aktørId: String): String {
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
        return result.fold(
            {
                response.headers["Location"].first()
            },
            { fuelError ->
                throw RegelApiBehovHttpClientException(
                    "Failed to run behov. Response message ${response.responseMessage}. Error message: ${fuelError.message}"
                )
            }
        )
    }

    private fun createBehovRequest(aktørId: String): BehovRequest {
        val kontekst = RegelKontekst("-12345", "soknad")
        return BehovRequest(aktørId, kontekst, LocalDate.now())
    }

    data class RegelKontekst(val id: String, val type: String)
}

class RegelApiBehovHttpClientException(override val message: String) : RuntimeException(message)
