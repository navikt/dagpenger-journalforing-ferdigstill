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
        .labelNames("opprettet", "grunn", "enhet")
        .register()

    fun automatiskJournalførtJaTellerInc(enhet: String) =
        automatiskJournalførtTeller.labels("true", "arena_ok", enhet).inc()

    fun automatiskJournalførtNeiTellerInc(reason: String, enhet: String) =
        automatiskJournalførtTeller.labels("false", reason, enhet).inc()

    private val inngangsvilkårResultatTeller = Counter
        .build()
        .name("inngangsvilkaar_resultat_journalfort")
        .help("Antall søknader som oppfyller / ikke oppfyller inngangsvilkårene vi tester")
        .labelNames("oppfyller")
        .register()

    fun inngangsvilkårResultatTellerInc(oppfyllerKrav: Boolean) =
        inngangsvilkårResultatTeller.labels(oppfyllerKrav.toString()).inc()

    val antallArbeidsforhold: Counter = Counter
        .build()
        .namespace(DAGPENGER_NAMESPACE)
        .name("soknad_arbeidsforhold")
        .help("Antall arbeidsforhold i en søknad")
        .labelNames("arbeidsforhold")
        .register()

    val språk: Counter = Counter
        .build()
        .namespace(DAGPENGER_NAMESPACE)
        .name("soknad_sprak")
        .help("Språk i en søknad")
        .labelNames("sprak")
        .register()

    val andreYtelser: Counter = Counter
        .build()
        .namespace(DAGPENGER_NAMESPACE)
        .name("soknad_andre_ytelser")
        .help("Om søknaden har andre ytelser")
        .labelNames("andre_ytelser")
        .register()

    val utdanning: Counter = Counter
        .build()
        .namespace(DAGPENGER_NAMESPACE)
        .name("soknad_utdanning")
        .help("Om søkeren er under utdanning")
        .labelNames("utdanning")
        .register()

    val arbeidstilstand: Counter = Counter
        .build()
        .namespace(DAGPENGER_NAMESPACE)
        .name("soknad_arbeidstilstand")
        .help("Hva slags arbeidstilknyting søkeren har")
        .labelNames("arbeidstilstand")
        .register()

    val egenNæring: Counter = Counter
        .build()
        .namespace(DAGPENGER_NAMESPACE)
        .name("soknad_egen_naring")
        .help("Om søker har noe egen næring")
        .labelNames("egenNaring", "gardsbruk", "fangstOgFiske")
        .register()
}
