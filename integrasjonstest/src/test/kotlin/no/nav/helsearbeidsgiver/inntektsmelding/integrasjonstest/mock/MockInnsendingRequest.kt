package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.FullLonnIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Inntekt
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Naturalytelse
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NaturalytelseKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Refusjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.ÅrsakInnsending
import java.time.LocalDate

fun mockInnsendingRequest(): InnsendingRequest =
    InnsendingRequest(
        orgnrUnderenhet = "orgnr-bål",
        identitetsnummer = "fnr-fredrik",
        behandlingsdager = listOf(LocalDate.now().plusDays(5)),
        egenmeldingsperioder = listOf(
            Periode(
                fom = LocalDate.now(),
                tom = LocalDate.now().plusDays(2)
            )
        ),
        arbeidsgiverperioder = emptyList(),
        bestemmendeFraværsdag = LocalDate.now(),
        fraværsperioder = emptyList(),
        inntekt = Inntekt(
            bekreftet = true,
            beregnetInntekt = 32100.0.toBigDecimal(),
            endringÅrsak = null,
            manueltKorrigert = false
        ),
        fullLønnIArbeidsgiverPerioden = FullLonnIArbeidsgiverPerioden(
            utbetalerFullLønn = true,
            begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.ARBEID_OPPHOERT
        ),
        refusjon = Refusjon(
            utbetalerHeleEllerDeler = true,
            refusjonPrMnd = 200.0.toBigDecimal(),
            refusjonOpphører = LocalDate.now()
        ),
        naturalytelser = listOf(
            Naturalytelse(
                naturalytelse = NaturalytelseKode.KOSTDOEGN,
                dato = LocalDate.now(),
                beløp = 300.0.toBigDecimal()
            )
        ),
        årsakInnsending = ÅrsakInnsending.ENDRING,
        bekreftOpplysninger = true
    )
