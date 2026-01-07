package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding

fun Inntektsmelding.tittel(): String {
    val endringTillegg =
        when (aarsakInnsending) {
            AarsakInnsending.Ny -> null
            AarsakInnsending.Endring -> "endring"
        }

    val imType = type
    val typeTillegg =
        when (imType) {
            is Inntektsmelding.Type.Selvbestemt -> null
            is Inntektsmelding.Type.Fisker -> "fisker med hyre"
            is Inntektsmelding.Type.UtenArbeidsforhold -> "unntatt registrering i Aa-registeret"
            is Inntektsmelding.Type.Behandlingsdager -> "behandlingsdager"
            is Inntektsmelding.Type.Forespurt -> "arbeidsgiverperiode – ikke forespurt".takeIf { !imType.erAgpForespurt && agp != null }
            is Inntektsmelding.Type.ForespurtEkstern -> "arbeidsgiverperiode – ikke forespurt".takeIf { !imType.erAgpForespurt && agp != null }
        }

    val tillegg =
        listOfNotNull(endringTillegg, typeTillegg)
            .joinToString()
            .let {
                if (it.isNotBlank()) {
                    " ($it)"
                } else {
                    it
                }
            }

    return "Inntektsmelding for sykepenger$tillegg"
}
