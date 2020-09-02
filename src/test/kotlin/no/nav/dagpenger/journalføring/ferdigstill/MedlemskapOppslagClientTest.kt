package no.nav.dagpenger.journalføring.ferdigstill

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.journalføring.ferdigstill.MedlemskapOppslagClientTest.MockServer.azureAdClient
import no.nav.dagpenger.journalføring.ferdigstill.azuread.AzureAdClient

import org.junit.jupiter.api.Test
import java.io.Closeable
import java.time.LocalDate

class MedlemskapOppslagClientTest {
    private object MockServer : Closeable {
        fun azureAdStub() = instance.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/tokenService"))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                .willReturn(WireMock.okJson(adTokenJson))
        )

        fun mockMedlemskapPost(fnr: String, arbeidUtenforNorge: Boolean, fom: LocalDate, tom: LocalDate) = instance.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/"))
                .withRequestBody(equalToJson(requestBody(fnr, arbeidUtenforNorge, fom, tom)))
                .withHeader(HttpHeaders.Authorization, WireMock.equalTo("Bearer $token"))
                .withHeader(HttpHeaders.ContentType, WireMock.equalTo("application/json"))
                .willReturn(
                    WireMock.okJson(this::class.java.getResource("/eksempel-oppslag-respons.json").readText())
                )
        )

        private val instance by lazy {
            WireMockServer(WireMockConfiguration.options().notifier(ConsoleNotifier(true)).dynamicPort()).also {
                it.start()
            }
        }

        val baseUrl by lazy { instance.baseUrl() }

        fun requestBody(fnr: String, arbeidUtenforNorge: Boolean, fom: LocalDate, tom: LocalDate) =
            """
        {
            "fnr": "$fnr",
            "periode": {
                "fom": "$fom",
                "tom": "$tom"
            },
            "brukerinput": {
                "arbeidUtenforNorge": $arbeidUtenforNorge
            }
        }"""

        override fun close() {
            instance.stop()
        }

        private val token = "aValidAccessToken"
        private val adTokenJson =
            """
        {
            "access_token": "$token",
            "expires_in": "3600"
        }"""

        fun azureAdClient(baseUrl: String) =
            AzureAdClient(
                Configuration.AzureAd(
                    clientId = "clientId",
                    clientSecret = "secret",
                    tenant = "tenant",
                    authorityEndpoint = "abc",
                    tokenEndpoint = "$baseUrl/tokenService"
                )
            )
    }

    @Test
    fun `Client calls endpoint and receives response`() {
        val fnr = "123456778910"
        val arbeidUtenforNorge = false
        val fom = LocalDate.of(2020, 3, 1)
        val tom = LocalDate.of(2021, 1, 1)

        MockServer.use {
            MockServer.azureAdStub()
            MockServer.mockMedlemskapPost(fnr, arbeidUtenforNorge, fom, tom)
            val azureAd = azureAdClient(MockServer.baseUrl)
            val medlemskapOppslag = MedlemskapOppslagClient(MockServer.baseUrl, oidcTokenProvider = { azureAd.token("abc").access_token })
            runBlocking {
                val evaluering = medlemskapOppslag.hentMedlemskapsevaluering(fnr, false, fom, tom, "anything")
                evaluering.resultat.svar shouldBe "JA"
            }
        }
    }
}
