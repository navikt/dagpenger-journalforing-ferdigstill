package no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.matching.EqualToJsonPattern
import com.github.tomakehurst.wiremock.matching.EqualToPattern
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.events.moshiInstance
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class VilkårtesterIntegrationTest {
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

    val xApiKey = "regelApiKey"

    @BeforeEach
    fun configure() {
        WireMock.configureFor(server.port())
    }

    @Test
    fun ` henter bestått minsteinntekt for aktørid`() = runBlocking {
        val aktørId = "666"
        val behovId = "123"
        val behovLocationUrl = "/behov/status/$behovId"
        val subsumsjonsId = "112233"
        val subsumsjon = dummySubsumsjon(
            behovId = behovId,
            minsteinntektResultat = dummyMinsteinntekt(oppfyllerMinsteinntekt = true)
        )

        setUpBehovstarterStub(aktørId, behovLocationUrl)
        setUpBehovPollerStub(subsumsjonsId)
        setUpSubsumsjonStub(subsumsjonsId, subsumsjon)

        val client = Vilkårtester(server.baseUrl(), xApiKey)
        val resultat = client.hentMinsteArbeidsinntektVilkår(aktørId)

        resultat!!.harBeståttMinsteArbeidsinntektVilkår shouldBe subsumsjon.minsteinntektResultat!!.oppfyllerMinsteinntekt
    }

    private fun setUpSubsumsjonStub(subsumsjonsId: String, subsumsjon: Subsumsjon) {
        val subsumsjonResponse = moshiInstance.adapter(Subsumsjon::class.java)
            .toJson(subsumsjon)

        val equalToPattern = EqualToPattern("regelApiKey")
        WireMock.stubFor(
            WireMock.get(WireMock.urlEqualTo("/subsumsjon/$subsumsjonsId"))
                .withHeader("X-API-KEY", equalToPattern)
                .willReturn(
                    WireMock.aResponse()
                        .withBody(subsumsjonResponse)
                )
        )
    }

    private fun setUpBehovPollerStub(subsumsjonsId: String) {
        WireMock.stubFor(
            WireMock.get(WireMock.urlEqualTo("/behov/status/123"))
                .withHeader("X-API-KEY", EqualToPattern(xApiKey))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(303)
                        .withHeader("Location", "/subsumsjon/$subsumsjonsId")
                )
        )
    }

    private fun setUpBehovstarterStub(aktørId: String, behovLocationUrl: String) {
        val behovstarterResponse =
            """
                {
                        "status" : "PENDING"
                }
            """.trimIndent()

        val expectedRequest =
            """
                    {
                        "aktorId": "$aktørId",
                        "vedtakId": -12345,
                        "beregningsdato": "${LocalDate.now()}"
                    }
            """.trimIndent()

        WireMock.stubFor(
            WireMock.post(WireMock.urlEqualTo("/behov"))
                .withHeader("X-API-KEY", EqualToPattern(xApiKey))
                .withRequestBody(EqualToJsonPattern(expectedRequest, true, false))
                .willReturn(
                    WireMock.aResponse()
                        .withBody(behovstarterResponse)
                        .withHeader("Location", behovLocationUrl)
                )
        )
    }
}
