package no.nav.dagpenger.journalføring.ferdigstill

import io.kotlintest.shouldBe
import io.mockk.mockk
import no.nav.dagpenger.events.Packet
import org.junit.jupiter.api.Test

internal class FeatureToggleTest {

    @Test
    fun `By default journalforing feature is off`() {
        val journalFøringFerdigstill = mockk<JournalføringFerdigstill>(relaxed = true)
        val service = Application(Configuration(), journalFøringFerdigstill)

        val packetWithoutToggle = Packet().apply {
            this.putValue("journalpostId", "123")
            this.putValue("journalføringState", "MOTTATT")
        }

        val packetWithToggleFalse = Packet().apply {
            this.putValue("journalpostId", "123")
            this.putValue("journalføringState", "MOTTATT")
            this.putValue("toggleBehandleNySøknad", false)
        }

        service.filterPredicates().all { it.test("", packetWithToggleFalse) } shouldBe false
        service.filterPredicates().all { it.test("", packetWithoutToggle) } shouldBe false
    }

    @Test
    fun `Journalforing feature is on if toggle is on`() {
        val journalFøringFerdigstill = mockk<JournalføringFerdigstill>(relaxed = true)
        val service = Application(Configuration(), journalFøringFerdigstill)

        val packetWithToggle = Packet().apply {
            this.putValue("journalpostId", "123")
            this.putValue("journalføringState", "MOTTATT")
            this.putValue("toggleBehandleNySøknad", true)
        }

        service.filterPredicates().all { it.test("", packetWithToggle) } shouldBe true
    }
}
