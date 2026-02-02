package no.nav.hag.simba.utils.felles.utils

import no.nav.hag.simba.utils.felles.domene.InntektsmeldingIntern as Inntektsmelding

fun Inntektsmelding.Type.erForespurt(): Boolean =
    when (this) {
        is Inntektsmelding.Type.Forespurt,
        is Inntektsmelding.Type.ForespurtEkstern,
        -> true

        is Inntektsmelding.Type.Selvbestemt,
        is Inntektsmelding.Type.Fisker,
        is Inntektsmelding.Type.UtenArbeidsforhold,
        is Inntektsmelding.Type.Behandlingsdager,
        -> false
    }
