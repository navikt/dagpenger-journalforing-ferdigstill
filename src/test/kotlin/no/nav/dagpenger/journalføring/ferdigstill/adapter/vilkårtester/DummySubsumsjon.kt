package no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester

fun dummySubsumsjon(behovId: String = "abc", minsteinntektResultat: MinsteinntektResultat? = null) = Subsumsjon(
    behovId = behovId,
    minsteinntektResultat = minsteinntektResultat
)

fun dummyMinsteinntekt(oppfyllerMinsteinntekt: Boolean = true) =
    MinsteinntektResultat("", "", oppfyllerMinsteinntekt, "", emptyList(), Beregningsregel.ORDINAER)
