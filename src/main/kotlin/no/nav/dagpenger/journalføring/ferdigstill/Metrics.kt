package no.nav.dagpenger.journalføring.ferdigstill

import io.prometheus.client.Counter

internal object Metrics {

    private val DAGPENGER_NAMESPACE = "dagpenger"

    private val jpFerdigstiltCounter = Counter
        .build()
        .namespace(DAGPENGER_NAMESPACE)
        .name("journalpost_ferdigstilt")
        .help("Number of journal post processed succesfully")
        .register()

    fun jpFerdigStillInc() = jpFerdigstiltCounter.inc()

    private val automatiskJournalførtTeller = Counter
        .build()
        .name("automatisk_journalfort_arena")
        .help("Antall søknader som er automatisk journalført i Arena")
        .labelNames("opprettet", "grunn")
        .register()

    fun automatiskJournalførtJaTellerInc() = automatiskJournalførtTeller.labels("true", "arena_ok").inc()
    fun automatiskJournalførtNeiTellerInc(reason: String) = automatiskJournalførtTeller.labels("false", reason).inc()

    private val inngangsvilkårResultatTeller = Counter
        .build()
        .name("inngangsvilkaar_resultat_journalfort")
        .help("Antall søknader som oppfyller / ikke oppfyller inngangsvilkårene vi tester")
        .labelNames("oppfyller")
        .register()

    fun inngangsvilkårResultatTellerInc(oppfyllerKrav: Boolean) = inngangsvilkårResultatTeller.labels(oppfyllerKrav.toString()).inc()
}
