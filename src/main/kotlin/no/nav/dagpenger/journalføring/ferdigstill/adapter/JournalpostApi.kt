package no.nav.dagpenger.journalføring.ferdigstill.adapter

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.httpPatch
import com.github.kittinunf.fuel.httpPut
import com.github.kittinunf.result.Result
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import mu.KotlinLogging
import no.nav.dagpenger.journalføring.ferdigstill.AdapterException
import no.nav.dagpenger.oidc.OidcClient
import java.util.regex.Pattern

private val logger = KotlinLogging.logger {}

internal interface JournalpostApi {
    fun ferdigstill(journalpostId: String)
    fun oppdater(journalpostId: String, jp: OppdaterJournalpostPayload)
}

internal data class OppdaterJournalpostPayload(
    val avsenderMottaker: Avsender,
    val bruker: Bruker,
    val tittel: String,
    val sak: Sak,
    val dokumenter: List<Dokument>,
    val behandlingstema: String = "ab0001",
    val tema: String = "DAG",
    val journalfoerendeEnhet: String = "9999"
)

internal enum class SaksType {
    GENERELL_SAK,
    FAGSAK
}

data class Bruker(val id: String, val idType: String = "FNR")
internal data class Sak(val saksType: SaksType, val fagsakId: String?, val fagsaksystem: String?)
internal data class Avsender(val navn: String)
internal data class Dokument(val dokumentInfoId: String, val tittel: String)

internal class JournalpostRestApi(private val url: String, private val oidcClient: OidcClient) :
    JournalpostApi {

    private val whitelistFeilmeldinger = setOf<String>(
        "Bruker kan ikke oppdateres for journalpost med journalpostStatus=J og journalpostType=I.",
        "er ikke midlertidig journalført"
    )

    private val feilmelding: Pattern = Pattern.compile("\\(type=([^,]+), status=([^)]+)\\)\\.<\\/div><div>([^<]+)")
    val feilmeldingRegex = Regex("\\(type=([^,]+), status=([^)]+)\\)\\.<\\/div><div>([^<]+)")

    init {
        FuelManager.instance.forceMethods = true
    }

    companion object {
        private val moshiInstance = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        fun toJsonPayload(jp: OppdaterJournalpostPayload): String =
            moshiInstance.adapter<OppdaterJournalpostPayload>(
                OppdaterJournalpostPayload::class.java
            ).toJson(jp)
    }

    override fun oppdater(journalpostId: String, jp: OppdaterJournalpostPayload) {
        val (_, _, result) = retryFuel {
            url.plus("/rest/journalpostapi/v1/journalpost/$journalpostId")
                .httpPut()
                .authentication()
                .bearer(oidcClient.oidcToken().access_token)
                .jsonBody(
                    toJsonPayload(
                        jp
                    )
                )
                .response()
        }

        when (result) {
            is Result.Success -> return
            is Result.Failure -> {
                if (sjekkTilstand(result, journalpostId)) return
                logger.error(
                    "Feilet oppdatering av journalpost: $journalpostId, respons fra journalpostapi ${result.error.response}",
                    result.error.exception
                )
                throw AdapterException(result.error.exception)
            }
        }
    }

    override fun ferdigstill(journalpostId: String) {
        val (_, _, result) =
            retryFuel {
                url.plus("/rest/journalpostapi/v1/journalpost/$journalpostId/ferdigstill")
                    .httpPatch()
                    .authentication()
                    .bearer(oidcClient.oidcToken().access_token)
                    .jsonBody("""{"journalfoerendeEnhet": "9999"}""")
                    .response()
            }

        when (result) {
            is Result.Success -> return
            is Result.Failure -> {
                if (sjekkTilstand(result, journalpostId)) return
                logger.error(
                    "Feilet ferdigstilling av journalpost: : $journalpostId, respons fra journalpostapi ${result.error.response}",
                    result.error.exception
                )
                throw AdapterException(result.error.exception)
            }
        }
    }

    private fun sjekkTilstand(
        result: Result.Failure<FuelError>,
        journalpostId: String
    ): Boolean {
        val body = result.error.response.data.toString(Charsets.UTF_8)
        val match = feilmeldingRegex.find(body)?.groups?.last()

        val matches = whitelistFeilmeldinger.count {
            match?.value?.contains(it) ?: false
        }

        if (matches >= 1) {
            logger.warn { "Journalpost $journalpostId i en tilstand som er ok. Tilstand: ${match?.value}" }
            return true
        }
        return false
    }
}
