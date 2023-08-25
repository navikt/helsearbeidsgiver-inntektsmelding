package no.nav.helsearbeidsgiver.felles.test.mock

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.FullLonnIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Inntekt
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Naturalytelse
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NaturalytelseKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Refusjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.ÅrsakInnsending
import java.math.BigDecimal
import java.time.LocalDate

object TestData {
    const val validIdentitetsnummer = "20015001543"
    const val validOrgNr = "123456785"
}

val GYLDIG_INNSENDING_REQUEST = InnsendingRequest(
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
    Inntekt(true, 32100.0.toBigDecimal(), endringÅrsak = null, false),
    FullLonnIArbeidsgiverPerioden(
        true,
        BegrunnelseIngenEllerRedusertUtbetalingKode.ARBEID_OPPHOERT
    ),
    Refusjon(true, 200.0.toBigDecimal(), LocalDate.now()),
    listOf(
        Naturalytelse(
            NaturalytelseKode.KOSTDOEGN,
            LocalDate.now(),
            300.0.toBigDecimal()
        )
    ),
    ÅrsakInnsending.ENDRING,
    true,
    "+4722222222"
)

val DELVIS_INNSENDING_REQUEST = InnsendingRequest(

    orgnrUnderenhet = TestData.validOrgNr,
    identitetsnummer = TestData.validIdentitetsnummer,
    behandlingsdager = emptyList(),
    egenmeldingsperioder = emptyList(),
    arbeidsgiverperioder = emptyList(),
    bestemmendeFraværsdag = LocalDate.of(2001, 1, 1),
    fraværsperioder = emptyList(),
    inntekt = Inntekt(
        bekreftet = true,
        beregnetInntekt = BigDecimal.TEN,
        endringÅrsak = null,
        manueltKorrigert = false
    ),
    fullLønnIArbeidsgiverPerioden = null,
    refusjon = Refusjon(false, null, null, null),
    naturalytelser = emptyList(),
    årsakInnsending = ÅrsakInnsending.NY,
    bekreftOpplysninger = true,
    forespurtData = listOf("inntekt", "refusjon")

)
