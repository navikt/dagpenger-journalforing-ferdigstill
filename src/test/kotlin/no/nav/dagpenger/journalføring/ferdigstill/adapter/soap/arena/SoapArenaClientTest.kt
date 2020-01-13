package no.nav.dagpenger.journalføring.ferdigstill.adapter.soap.arena

import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaSakStatus
import no.nav.dagpenger.journalføring.ferdigstill.adapter.HentArenaSakerException
import no.nav.dagpenger.streams.HealthStatus
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.BehandleArbeidOgAktivitetOppgaveV1
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.meldinger.WSBestillOppgaveResponse
import no.nav.tjeneste.virksomhet.ytelseskontrakt.v3.YtelseskontraktV3
import no.nav.tjeneste.virksomhet.ytelseskontrakt.v3.informasjon.ytelseskontrakt.WSDagpengekontrakt
import no.nav.tjeneste.virksomhet.ytelseskontrakt.v3.meldinger.WSHentYtelseskontraktListeResponse
import org.junit.jupiter.api.Test

internal class SoapArenaClientTest {

    @Test
    fun `suksessfull bestillOppgave gir arenaSakId `() {
        val stubbedClient = mockk<BehandleArbeidOgAktivitetOppgaveV1>()
        every { stubbedClient.bestillOppgave(any()) } returns WSBestillOppgaveResponse().withArenaSakId("123")

        val client = SoapArenaClient(stubbedClient, mockk())

        val actual = client.bestillOppgave("123456789", "abcbscb", "beskrivelse")

        actual shouldBe "123"
    }

    @Test
    fun `Skal kunne hente arena saker basert på bruker id`() {

        val ytelseskontraktV3: YtelseskontraktV3 = mockk()

        every {
            ytelseskontraktV3.hentYtelseskontraktListe(any())
        } returns WSHentYtelseskontraktListeResponse().withYtelseskontraktListe(listOf(WSDagpengekontrakt().withFagsystemSakId(123).withStatus("Inaktiv").withYtelsestype("Dagpenger")))

        val client = SoapArenaClient(mockk(), ytelseskontraktV3)
        val saker = client.hentArenaSaker("1234")

        saker.isEmpty() shouldBe false
        saker.first().fagsystemSakId shouldBe 123
        saker.first().status shouldBe ArenaSakStatus.Inaktiv
    }

    @Test
    fun `Skal kaste unntak med ukjent saksstatus`() {

        val ytelseskontraktV3: YtelseskontraktV3 = mockk()

        every {
            ytelseskontraktV3.hentYtelseskontraktListe(any())
        } returns WSHentYtelseskontraktListeResponse().withYtelseskontraktListe(listOf(WSDagpengekontrakt().withFagsystemSakId(123).withStatus("INAKT").withYtelsestype("Dagpenger")))

        val client = SoapArenaClient(mockk(), ytelseskontraktV3)

        shouldThrow<HentArenaSakerException> {
            client.hentArenaSaker("1234")
        }
    }

    @Test
    fun ` helsesjekk skal være ok når Arena er oppe`() {
        val ytelseskontraktV3: YtelseskontraktV3 = mockk()
        every {
            ytelseskontraktV3.ping()
        } returns Unit

        val client = SoapArenaClient(mockk(), ytelseskontraktV3)
        client.status() shouldBe HealthStatus.UP
    }

    @Test
    fun ` helsesjekk skal ikke være ok når Arena nede`() {
        val ytelseskontraktV3: YtelseskontraktV3 = mockk()
        every {
            ytelseskontraktV3.ping()
        } throws RuntimeException()

        val client = SoapArenaClient(mockk(), ytelseskontraktV3)
        client.status() shouldBe HealthStatus.DOWN
    }
}