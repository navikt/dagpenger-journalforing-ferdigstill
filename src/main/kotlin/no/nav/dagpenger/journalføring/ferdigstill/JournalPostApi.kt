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

internal interface JournalPostApi {
    fun ferdigstill(journalPostId: String)
    fun oppdater(journalPostId: String, jp: OppdaterJournalPostPayload)
}

internal data class OppdaterJournalPostPayload(
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
internal data class Dokument(val dokumentinfoId: String, val brevkode: String, val tittel: String)

internal class JournalPostRestApi(private val url: String, private val oidcClient: OidcClient) : JournalPostApi {
    init {
        FuelManager.instance.forceMethods = true
    }

    companion object {
        private val moishiInstance = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        fun toJsonPayload(jp: OppdaterJournalPostPayload): String =
            moishiInstance.adapter<OppdaterJournalPostPayload>(OppdaterJournalPostPayload::class.java).toJson(jp)
    }

    override fun oppdater(journalPostId: String, jp: OppdaterJournalPostPayload) {
        val (_, _, result) = url.plus("/rest/journalpostapi/v1/journalpost/$journalPostId")
            .httpPut()
            .authentication()
            .bearer(oidcClient.oidcToken().access_token)
            .jsonBody(toJsonPayload(jp))
            .response()

        result.fold(
            { logger.info("Oppdatert journal post: $journalPostId") },
            { e ->
                logger.error("Feilet oppdatering av journalpost: $journalPostId", e.exception)
                throw e
            }
        )
    }

    override fun ferdigstill(journalPostId: String) {
        val (_, _, result) =
            url.plus("/rest/journalpostapi/v1/journalpost/$journalPostId/ferdigstill")
                .httpPatch()
                .authentication()
                .bearer(oidcClient.oidcToken().access_token)
                .jsonBody("""{"journalfoerendeEnhet": "9999"}""")
                .response()

        result.fold(
            { logger.info("Ferdigstilt journal post: $journalPostId") },
            { e ->
                logger.error("Feilet ferdigstilling av journalpost: : $journalPostId", e.exception)
                throw e
            }
        )
    }
}
