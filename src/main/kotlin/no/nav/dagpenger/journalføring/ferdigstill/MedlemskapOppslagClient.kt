package no.nav.dagpenger.journalføring.ferdigstill

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import java.time.LocalDate
import mu.KotlinLogging
import mu.withLoggingContext

private val sikkerlogg = KotlinLogging.logger("tjenestekall.medlemskap.oppslag.client")

@KtorExperimentalAPI
class MedlemskapOppslagClient(
    private val endpoint: String,
    val oidcTokenProvider: () -> String
) {
    private val client = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = JacksonSerializer() {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                registerModule(JavaTimeModule())
            }
        }

        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) = sikkerlogg.info { message }
            }
            level = LogLevel.ALL
        }
    }

    suspend fun hentMedlemskapsevaluering(
        fnr: String,
        arbeidUtenforNorge: Boolean,
        fom: LocalDate,
        tom: LocalDate,
        behovId: String
    ): Medlemskapsevaluering {
        withLoggingContext(
            "behovId" to behovId
        ) {
            return client.post(endpoint) {
                header(Authorization, "Bearer ${oidcTokenProvider()}")
                header("Nav-Call-Id", behovId)
                contentType(ContentType.Application.Json)
                body = MedlemskapRequest(
                    fnr,
                    MedlemskapRequest.Periode(fom, tom),
                    MedlemskapRequest.Brukerinput(arbeidUtenforNorge)
                ).also {
                    sikkerlogg.info { "Sender request for medlemskap: $it" }
                }
            }
        }
    }
}

data class MedlemskapRequest(
    val fnr: String,
    val periode: Periode,
    val brukerinput: Brukerinput
) {
    data class Periode(val fom: LocalDate, val tom: LocalDate)
    data class Brukerinput(val arbeidUtenforNorge: Boolean)
}
