package no.nav.helsearbeidsgiver.inntektsmelding.berikinntektsmeldingservice

import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.ForespurtData
import no.nav.helsearbeidsgiver.felles.domene.ForslagInntekt
import no.nav.helsearbeidsgiver.felles.domene.ForslagRefusjon

fun Forespoersel.utenPaakrevdAGP(): Forespoersel =
    copy(
        forespurtData =
            forespurtData.copy(
                arbeidsgiverperiode =
                    ForespurtData.Arbeidsgiverperiode(
                        paakrevd = false,
                    ),
            ),
    )

fun Forespoersel.utenPaakrevdInntekt(): Forespoersel =
    copy(
        forespurtData =
            forespurtData.copy(
                inntekt =
                    ForespurtData.Inntekt(
                        paakrevd = false,
                        forslag =
                            ForslagInntekt.Fastsatt(
                                fastsattInntekt = 8795.0,
                            ),
                    ),
            ),
    )

fun Forespoersel.utenPaakrevdRefusjon(): Forespoersel =
    copy(
        forespurtData =
            forespurtData.copy(
                refusjon =
                    ForespurtData.Refusjon(
                        paakrevd = false,
                        forslag =
                            ForslagRefusjon(
                                perioder = emptyList(),
                                opphoersdato = null,
                            ),
                    ),
            ),
    )
