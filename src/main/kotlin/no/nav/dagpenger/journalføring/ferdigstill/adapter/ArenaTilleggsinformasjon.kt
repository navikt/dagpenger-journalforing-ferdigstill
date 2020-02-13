package no.nav.dagpenger.journalføring.ferdigstill.adapter

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun createArenaTilleggsinformasjon(dokumentTitler: List<String>, registrertDato: ZonedDateTime): String {
    val hovedDokument = dokumentTitler.first()
    val vedlegg = dokumentTitler.drop(1)

    val formatertVedlegg =
            if (vedlegg.isNotEmpty()) {
                vedlegg.joinToString(prefix = "- ", separator = "\n- ", postfix = "\n")
            } else {
                ""
            }
    val formatertDato = registrertDato.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    val datoBeskrivelse = "Registrert dato: ${formatertDato}\n"

    return "Hoveddokument: ${hovedDokument}\n" +
            formatertVedlegg +
            datoBeskrivelse +
            "Dokumentet er skannet inn og journalført automatisk av digitale dagpenger. Gjennomfør rutinen \"Etterkontroll av automatisk journalførte dokumenter\"."
}
