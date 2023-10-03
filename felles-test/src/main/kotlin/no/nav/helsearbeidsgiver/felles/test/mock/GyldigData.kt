package no.nav.helsearbeidsgiver.felles.test.mock

import no.nav.helsearbeidsgiver.domene.inntektsmelding.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.FullLoennIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Naturalytelse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.NaturalytelseKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Refusjon
import java.time.LocalDate

object TestData {
    const val validIdentitetsnummer = "20015001543"
    const val validOrgNr = "123456785"
}

val GYLDIG_INNSENDING_REQUEST = Innsending(
    TestData.validOrgNr,
    TestData.validIdentitetsnummer,
    listOf(LocalDate.now().plusDays(5)),
    listOf(
        Periode(
            LocalDate.now(),
            LocalDate.now().plusDays(2)
        )
    ),
    arbeidsgiverperioder = listOf(
        Periode(
            LocalDate.now(),
            LocalDate.now().plusDays(2)
        )
    ),
    LocalDate.now(),
    emptyList(),
    Inntekt(true, 32100.0, endringÅrsak = null, false),
    FullLoennIArbeidsgiverPerioden(
        true,
        BegrunnelseIngenEllerRedusertUtbetalingKode.ArbeidOpphoert
    ),
    Refusjon(true, 200.0, LocalDate.now()),
    listOf(
        Naturalytelse(
            NaturalytelseKode.KOSTDOEGN,
            LocalDate.now(),
            300.0
        )
    ),
    AarsakInnsending.ENDRING,
    true,
    "+4722222222"
)

val DELVIS_INNSENDING_REQUEST = Innsending(

    orgnrUnderenhet = TestData.validOrgNr,
    identitetsnummer = TestData.validIdentitetsnummer,
    behandlingsdager = emptyList(),
    egenmeldingsperioder = emptyList(),
    arbeidsgiverperioder = emptyList(),
    bestemmendeFraværsdag = LocalDate.of(2001, 1, 1),
    fraværsperioder = emptyList(),
    inntekt = Inntekt(
        bekreftet = true,
        beregnetInntekt = 10.0,
        endringÅrsak = null,
        manueltKorrigert = false
    ),
    fullLønnIArbeidsgiverPerioden = null,
    refusjon = Refusjon(false, null, null, null),
    naturalytelser = emptyList(),
    årsakInnsending = AarsakInnsending.NY,
    bekreftOpplysninger = true,
    forespurtData = listOf("inntekt", "refusjon"),
    telefonnummer = "22555555"

)
