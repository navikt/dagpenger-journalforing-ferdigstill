package no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester

fun dummySubsumsjon(behovId: String = "abc", minsteinntektResultat: MinsteinntektResultat? = null) = Subsumsjon(
    behovId = behovId,
    grunnlagResultat = null,
    minsteinntektResultat = minsteinntektResultat,
    periodeResultat = null,
    satsResultat = null
)

fun dummyMinsteinntekt(oppfyllerMinsteinntekt: Boolean = true) =
    MinsteinntektResultat("", "", oppfyllerMinsteinntekt, "", emptyList())
