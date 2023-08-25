package no.nav.helsearbeidsgiver.felles.test.mock

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.FullLonnIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Inntekt
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Naturalytelse
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NaturalytelseKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NyStillingsprosent
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Refusjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.ÅrsakInnsending
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

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

val INNTEKTSMELDING_DOK_MED_GAMMEL_INNTEKT = InntektsmeldingDokument(
    orgnrUnderenhet = TestData.validOrgNr,
    identitetsnummer = TestData.validIdentitetsnummer,
    fulltNavn = "Testnavn",
    virksomhetNavn = "Test A/S",
    behandlingsdager = emptyList(),
    egenmeldingsperioder = emptyList(),
    bestemmendeFraværsdag = LocalDate.now(),
    fraværsperioder = emptyList(),
    arbeidsgiverperioder = emptyList(),
    beregnetInntekt = BigDecimal.ONE,
    inntekt = null,
    fullLønnIArbeidsgiverPerioden = FullLonnIArbeidsgiverPerioden(utbetalerFullLønn = true),
    refusjon = Refusjon(false),
    naturalytelser = null,
    tidspunkt = OffsetDateTime.now(),
    årsakInnsending = ÅrsakInnsending.NY,
    identitetsnummerInnsender = null,
    telefonnummer = "22555555"
)

val DOK_MED_NY_INNTEKT =
    INNTEKTSMELDING_DOK_MED_GAMMEL_INNTEKT.copy(inntekt = Inntekt(true, BigDecimal.ONE, NyStillingsprosent(LocalDate.of(2020, 1, 1)), true))
