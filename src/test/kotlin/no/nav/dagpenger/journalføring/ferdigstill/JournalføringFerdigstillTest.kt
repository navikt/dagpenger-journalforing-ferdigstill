package no.nav.dagpenger.journalføring.ferdigstill

import no.nav.dagpenger.events.avro.Behov
import no.nav.dagpenger.events.avro.Ettersending
import no.nav.dagpenger.events.avro.Søknad
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JournalføringFerdigstillTest {

    private fun basicBehovBuilder(): Behov.Builder {
        return Behov
            .newBuilder()
            .setBehovId("000")
    }

    @Test
    fun `Do not process behov without specified fagsak id`() {

        val behovError = basicBehovBuilder()
            .setTrengerManuellBehandling(false)
            .setHenvendelsesType(Søknad())
            .build()

        assertFalse(shouldBeProcessed(behovError))
    }

    @Test
    fun `Do not process behov without specified henvendelses type`() {

        val behovError = basicBehovBuilder()
            .setFagsakId("123")
            .setTrengerManuellBehandling(false)
            .build()

        assertFalse(shouldBeProcessed(behovError))
    }

    @Test
    fun `Do process behov where henvendelses type is 'Søknad'`() {

        val behovError = basicBehovBuilder()
            .setFagsakId("123")
            .setTrengerManuellBehandling(false)
            .setHenvendelsesType(Søknad())
            .build()

        assertTrue(shouldBeProcessed(behovError))
    }

    @Test
    fun `Do process behov where henvendelses type is 'Ettersending'`() {

        val behovError = basicBehovBuilder()
            .setFagsakId("123")
            .setTrengerManuellBehandling(false)
            .setHenvendelsesType(Ettersending())
            .build()

        assertTrue(shouldBeProcessed(behovError))
    }
}
