package no.nav.dagpenger.journalføring.ferdigstill

import io.mockk.mockk
import io.mockk.verify
import no.finn.unleash.DefaultUnleash
import no.finn.unleash.FakeUnleash
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.ARENA_SAK_ID
import org.junit.jupiter.api.Test

internal class FeatureToggleTest {

    private val packet = Packet().apply {
        this.putValue(ARENA_SAK_ID, "arenaSakid")
    }

    private val configuration = Configuration()

    @Test
    fun `By default journalforing feature is off`() {
        val journalFøringFerdigstill = mockk<JournalFøringFerdigstill>(relaxed = true)

        Application(configuration, journalFøringFerdigstill, DefaultUnleash(configuration.unleashConfig)).onPacket(packet)

        verify(exactly = 0) {
            journalFøringFerdigstill.handlePacket(any())
        }
    }

    @Test
    fun `Journalforing feature is on if toggle is on`() {
        val journalFøringFerdigstill = mockk<JournalFøringFerdigstill>(relaxed = true)

        Application(configuration, journalFøringFerdigstill, FakeUnleash().apply {
            this.enable(JOURNALFØRING_FEATURE_TOGGLE_NAME)
        }).onPacket(packet)

        verify(exactly = 1) {
            journalFøringFerdigstill.handlePacket(any())
        }
    }
}
