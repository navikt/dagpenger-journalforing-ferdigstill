package no.nav.dagpenger.journalføring.ferdigstill

import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.events.Packet
import org.junit.jupiter.api.Test

internal class FeatureToggleTest {

    private val configuration = Configuration()

    @Test
    fun `By default journalforing feature is off`() {
        val journalFøringFerdigstill = mockk<JournalFøringFerdigstill>(relaxed = true)

        val packetWithoutToggle = Packet().apply {
        }

        Application(configuration, journalFøringFerdigstill).onPacket(packetWithoutToggle)

        verify(exactly = 0) {
            journalFøringFerdigstill.handlePacket(any())
        }

        val packetWithToggleFalse = Packet().apply {
            this.putValue("toggleBehandleNySøknad", false)
        }

        Application(configuration, journalFøringFerdigstill).onPacket(packetWithToggleFalse)

        verify(exactly = 0) {
            journalFøringFerdigstill.handlePacket(any())
        }
    }

    @Test
    fun `Journalforing feature is on if toggle is on`() {
        val journalFøringFerdigstill = mockk<JournalFøringFerdigstill>(relaxed = true)

        val packet = Packet().apply {
            this.putValue("toggleBehandleNySøknad", true)
        }

        Application(configuration, journalFøringFerdigstill).onPacket(packet)

        verify(exactly = 1) {
            journalFøringFerdigstill.handlePacket(any())
        }
    }
}
