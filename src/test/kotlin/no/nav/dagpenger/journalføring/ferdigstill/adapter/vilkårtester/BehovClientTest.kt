package no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.matching.EqualToJsonPattern
import com.github.tomakehurst.wiremock.matching.EqualToPattern
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BehovClientTest {
    companion object {
        val server: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())

        @BeforeAll
        @JvmStatic
        fun start() {
            server.start()
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            server.stop()
        }
    }

    @BeforeEach
    fun configure() {
        WireMock.configureFor(server.port())
    }

    @Test
    fun ` Should start behov `() {
        val aktørId = "001"

        val responseBody =
            """
                {
                        "status" : "PENDING"
                }
            """.trimIndent()

        val equalToPattern = EqualToPattern("regelApiKey")
        WireMock.stubFor(
            WireMock.post(WireMock.urlEqualTo("//behov"))
                .withHeader("X-API-KEY", equalToPattern)
                .willReturn(
                    WireMock.aResponse()
                        .withBody(responseBody)
                        .withHeader("Location", "/behov/status/123")
                )
        )

        val client = BehovClient(server.url(""), equalToPattern.value)

        client.startBehov(aktørId)

        val expectedRequest =
            """
                    {
                        "aktorId": "$aktørId",
                        "regelkontekst" : {
                        "id" : "-12345",
                        "type" : "soknad"
                        },
                        "vedtakId": -12345,
                        "beregningsdato": "${LocalDate.now()}"
                    }
            """.trimIndent()

        server.verify(
            1,
            WireMock.postRequestedFor(WireMock.urlMatching("//behov"))
                .withRequestBody(EqualToJsonPattern(expectedRequest, true, false))
                .withHeader("Content-Type", WireMock.equalTo("application/json"))
        )
    }
}
