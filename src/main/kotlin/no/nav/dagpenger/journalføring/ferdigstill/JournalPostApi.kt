package no.nav.dagpenger.journalføring.ferdigstill

import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.httpPatch
import com.github.kittinunf.fuel.httpPut
import mu.KotlinLogging
import no.nav.dagpenger.oidc.OidcClient

private val logger = KotlinLogging.logger {}

internal interface JournalPostApi {
    fun ferdigstill(journalPostId: String)
    fun oppdater(journalPostId: String, sak: Sak)
}

internal class JournalPostRestApi(private val url: String, private val oidcClient: OidcClient) : JournalPostApi {

    override fun oppdater(journalPostId: String, sak: Sak) {
        url.plus("/rest/journalpostapi/v1/journalpost/$journalPostId")
            .httpPut()
            .authentication()
            .bearer(oidcClient.oidcToken().access_token)
            .jsonBody(sak.toJsonString())
            .response { _, _, result ->
                result.fold(
                    { logger.info("Oppdatert journal post: $journalPostId") },
                    { e ->
                        logger.error("Feilet oppdatering av journalpost: $journalPostId", e.exception)
                        throw e
                    }
                )
            }
    }

    override fun ferdigstill(journalPostId: String) {
        url.plus("/rest/journalpostapi/v1/journalpost/$journalPostId/ferdigstill")
            .httpPatch()
            .authentication()
            .bearer(oidcClient.oidcToken().access_token)
            .jsonBody("""{"journalfoerendeEnhet": "9999"}""")
            .response { _, _, result ->
                result.fold(
                    { logger.info("Ferdigstilt journal post: $journalPostId") },
                    { e ->
                        logger.error("Feilet ferdigstillin av journalpost: : $journalPostId", e.exception)
                        throw e
                    }
                )
            }
    }
}
