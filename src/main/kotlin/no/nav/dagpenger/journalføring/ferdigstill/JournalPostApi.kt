package no.nav.dagpenger.journalføring.ferdigstill

import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.httpPatch
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

internal interface JournalPostApi {
    fun ferdigStill(journalPostId: String)
}


internal class JournalPostRestApi(private val url: String) : JournalPostApi {

    override fun ferdigStill(journalPostId: String) {
        url.plus("/rest/journalpostapi/v1/journalpost/$journalPostId/ferdigstill")
            .httpPatch()
            .jsonBody("""{"journalfoerendeEnhet": "9999"}""")
            .response { _, _, result ->
                result.fold(
                    { logger.info("Ferdigstilt journal post: $journalPostId") },
                    { e -> logger.error("Failed: $journalPostId", e.exception) }
                )
            }
    }

}
