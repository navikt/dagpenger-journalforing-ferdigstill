package no.nav.dagpenger.journalføring.ferdigstill

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.dagpenger.journalføring.ferdigstill.PacketMapper.dokumentJsonAdapter
import org.junit.jupiter.api.Test

class DokumentAdapterTest {
    @Test
    fun `Skal greie å konvertere dokumentInfo med flere felter til Dokument med tittel`() {

        val json =
            """[{"tittel": "en", "brevkode": "123", "dokumentInfoId": "hallo"}, {"tittel": "to", "dokumentInfoId": "hei"}]""".trimIndent()

        val konverterteDokumenter = dokumentJsonAdapter.fromJson(json)

        konverterteDokumenter shouldNotBe null
        konverterteDokumenter?.first()?.tittel shouldBe "en"
        konverterteDokumenter?.last()?.tittel shouldBe "to"
    }
}
