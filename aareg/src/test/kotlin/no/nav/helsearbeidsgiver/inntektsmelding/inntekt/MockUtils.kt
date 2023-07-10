package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import no.nav.helsearbeidsgiver.aareg.Ansettelsesperiode
import no.nav.helsearbeidsgiver.aareg.Arbeidsforhold
import no.nav.helsearbeidsgiver.aareg.Arbeidsgiver
import no.nav.helsearbeidsgiver.aareg.Opplysningspliktig
import no.nav.helsearbeidsgiver.aareg.Periode
import no.nav.helsearbeidsgiver.felles.ArbeidsforholdLøsning
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.tilArbeidsforhold
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.kl

fun mockLøsningSuccess(): ArbeidsforholdLøsning =
    ArbeidsforholdLøsning(
        value = mockKlientArbeidsforhold()
            .tilArbeidsforhold()
            .let(::listOf)
    )

fun mockLøsningFailure(): ArbeidsforholdLøsning =
    ArbeidsforholdLøsning(
        error = Feilmelding("Klarte ikke hente arbeidsforhold")
    )

fun mockKlientArbeidsforhold(): Arbeidsforhold =
    Arbeidsforhold(
        arbeidsgiver = Arbeidsgiver(
            type = "Underenhet",
            organisasjonsnummer = "810007842"
        ),
        opplysningspliktig = Opplysningspliktig(
            type = "ikke brukt",
            organisasjonsnummer = "ikke brukt heller"
        ),
        arbeidsavtaler = emptyList(),
        ansettelsesperiode = Ansettelsesperiode(
            Periode(
                fom = 1.januar,
                tom = 16.januar
            )
        ),
        registrert = 3.januar.kl(6, 30, 40, 50000)
    )
