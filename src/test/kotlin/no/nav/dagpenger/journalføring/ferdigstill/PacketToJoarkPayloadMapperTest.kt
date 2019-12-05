package no.nav.dagpenger.journalføring.ferdigstill

import io.kotlintest.shouldBe
import no.nav.dagpenger.events.Packet
import org.junit.jupiter.api.Test

internal class PacketToJoarkPayloadMapperTest {

    @Test
    fun `Exctract journal post id from packet`() {
        PacketToJoarkPayloadMapper.journalPostIdFrom(Packet().apply {
            this.putValue(PacketKeys.JOURNALPOST_ID, "journalPostId")
        }) shouldBe "journalPostId"
    }

    @Test
    fun `Exctract avsender from packet`() {
        PacketToJoarkPayloadMapper.avsenderFrom(Packet().apply {
            this.putValue(PacketKeys.AVSENDER_NAVN, "et navn")
        }).navn shouldBe "et navn"
    }

    @Test
    fun `Exctract bruker from packet`() {
        PacketToJoarkPayloadMapper.brukerFrom(Packet().apply {
            this.putValue(PacketKeys.FNR, "fnr")
        }).id shouldBe "fnr"
    }

    @Test
    fun `Exctract Arena sak from packet`() {
        val sak = PacketToJoarkPayloadMapper.sakFrom(Packet().apply {
            this.putValue(PacketKeys.ARENA_SAK_ID, "sakId")
        })

        sak.fagsakId shouldBe "sakId"
        sak.saksType shouldBe SaksType.FAGSAK
        sak.fagsaksystem shouldBe "AO01"
    }

    @Test
    fun `Exctract Generell sak from packet`() {
        val sak = PacketToJoarkPayloadMapper.sakFrom(Packet())

        sak.saksType shouldBe SaksType.GENERELL_SAK
        sak.fagsaksystem shouldBe null
        sak.fagsakId shouldBe null
    }

    @Test
    fun `Extract dokumenter from packet`() {
        val dokumenter = PacketToJoarkPayloadMapper.dokumenterFrom(Packet().apply {
            this.putValue(PacketKeys.DOKUMENTER, """
                [
                  {
                    "dokumentinfoId": "id1",
                    "brevkode": "kode1",
                    "tittel": "tittel1"
                  },
                  {
                    "dokumentinfoId": "id2",
                    "brevkode": "kode2",
                    "tittel": "tittel2"
                  }
                ]
            """.trimIndent())
        })

        dokumenter.size shouldBe 2

        with(dokumenter.first()) {
            this.dokumentinfoId shouldBe "id1"
            this.brevkode shouldBe "kode1"
            this.tittel shouldBe "tittel1"
        }
        with(dokumenter.last()) {
            this.dokumentinfoId shouldBe "id2"
            this.brevkode shouldBe "kode2"
            this.tittel shouldBe "tittel2"
        }
    }

    @Test
    fun `Extract journal post from packet`() {
        val jp = PacketToJoarkPayloadMapper.journalPostFrom(Packet().apply {
            this.putValue(PacketKeys.JOURNALPOST_ID, "journalPostId")
            this.putValue(PacketKeys.AVSENDER_NAVN, "navn")
            this.putValue(PacketKeys.FNR, "fnr")
            this.putValue(PacketKeys.DOKUMENTER, """
                [
                  {
                    "dokumentinfoId": "dokumentId",
                    "brevkode": "brevKode",
                    "tittel": "tittel"
                  }
                ] """)
        })

        jp.avsenderMottaker.navn shouldBe "navn"
        jp.behandlingstema shouldBe "ab0001"
        jp.bruker.id shouldBe "fnr"
        jp.bruker.idType shouldBe "FNR"
        jp.dokumenter shouldBe listOf(Dokument("dokumentId", "brevKode", "tittel"))
        jp.journalfoerendeEnhet shouldBe "9999"
        jp.sak.saksType shouldBe SaksType.GENERELL_SAK
        jp.tema shouldBe "DAG"
        jp.tittel shouldBe "tittel"
    }
}
