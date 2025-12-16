package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding

fun Inntektsmelding.tittelTillegg(): String? {
    val imType = type
    return when (imType) {
        is Inntektsmelding.Type.Selvbestemt -> null
        is Inntektsmelding.Type.Fisker -> "(Fisker m/hyre)"
        is Inntektsmelding.Type.UtenArbeidsforhold -> "(Unntatt registrering i Aa-registeret)"
        is Inntektsmelding.Type.Behandlingsdager -> "(Behandlingsdager)"
        is Inntektsmelding.Type.Forespurt -> "(Arbeidsgiverperiode – ikke forespurt)".takeIf { !imType.erAgpForespurt && agp != null }
        is Inntektsmelding.Type.ForespurtEkstern -> "(Arbeidsgiverperiode – ikke forespurt)".takeIf { !imType.erAgpForespurt && agp != null }
    }
}
