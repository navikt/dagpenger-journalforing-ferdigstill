package no.nav.dagpenger.journalføring.ferdigstill

import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.httpPatch
import com.github.kittinunf.fuel.httpPut
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import mu.KotlinLogging
import no.nav.dagpenger.oidc.OidcClient

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

enum class SaksType {
    GENERELL_SAK,
    FAGSAK
}

internal data class Sak(val saksType: SaksType, val fagsakId: String?, val fagsaksystem: String?)
internal data class Avsender(val navn: String)
internal data class Bruker(val id: String, val idType: String = "FNR")
internal data class Dokument(val dokumentInfoId: String, val tittel: String)

internal class JournalpostRestApi(private val url: String, private val oidcClient: OidcClient) : JournalpostApi {
    init {
        FuelManager.instance.forceMethods = true
    }

    companion object {
        private val moshiInstance = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        fun toJsonPayload(jp: OppdaterJournalpostPayload): String =
            moshiInstance.adapter<OppdaterJournalpostPayload>(OppdaterJournalpostPayload::class.java).toJson(jp)
    }

    override fun oppdater(journalpostId: String, jp: OppdaterJournalpostPayload) {
        val (_, _, result) = retryFuel {
            url.plus("/rest/journalpostapi/v1/journalpost/$journalpostId")
                .httpPut()
                .authentication()
                .bearer(oidcClient.oidcToken().access_token)
                .jsonBody(toJsonPayload(jp))
                .response()
        }

        result.fold(
            { logger.info("Oppdatert journalpost: $journalpostId") },
            { e ->
                logger.error("Feilet oppdatering av journalpost: $journalpostId", e.exception)
                throw AdapterException(e.exception)
            }
        )
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

        result.fold(
            { logger.info("Ferdigstilt journalpost: $journalpostId") },
            { e ->
                logger.error(
                    "Feilet ferdigstilling av journalpost: : $journalpostId, respons fra joark ${e.response}",
                    e.exception
                )
                throw AdapterException(e.exception)
            }
        )
    }
}
