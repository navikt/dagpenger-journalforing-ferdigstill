package no.nav.dagpenger.journalføring.ferdigstill

import io.mockk.mockk
import io.mockk.verifyAll
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.FNR
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.JOURNALPOST_ID
import org.junit.jupiter.api.Test

internal class JournalFøringFerdigstillTest {

    private val journalPostApi = mockk<JournalPostApi>(relaxed = true)

    @Test
    fun `Extract correct Packet values on calls to Api`() {

        val journalFøringFerdigstill = JournalFøringFerdigstill(journalPostApi)
        val journalPostId = "journalpostid"
        val fnr = "fødselnummer"

        journalFøringFerdigstill.handlePacket(Packet().apply {
            this.putValue(FNR, fnr)
            this.putValue(JOURNALPOST_ID, journalPostId)
        })

        verifyAll {
            journalPostApi.ferdigstill(journalPostId)
            journalPostApi.oppdater(fnr, journalPostId)
        }
    }
}
