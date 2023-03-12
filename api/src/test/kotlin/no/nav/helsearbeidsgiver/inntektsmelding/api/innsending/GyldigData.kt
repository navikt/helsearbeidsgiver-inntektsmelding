package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.FullLonnIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Inntekt
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Refusjon
import no.nav.helsearbeidsgiver.inntektsmelding.api.TestData
import java.time.LocalDate

val GYLDIG = InnsendingRequest(
    TestData.validOrgNr,
    TestData.validIdentitetsnummer,
    listOf(LocalDate.now().plusDays(5)),
    listOf(
        no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode(
            LocalDate.now(),
            LocalDate.now().plusDays(2)
        )
    ),
    emptyList(),
    LocalDate.now(),
    emptyList(),
    Inntekt(true, 32100.0.toBigDecimal(), endringÅrsak = null, false),
    FullLonnIArbeidsgiverPerioden(
        true,
        BegrunnelseIngenEllerRedusertUtbetalingKode.ARBEID_OPPHOERT
    ),
    Refusjon(true, 200.0.toBigDecimal(), LocalDate.now()),
    listOf(
        no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Naturalytelse(
            no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NaturalytelseKode.KOST_DOEGN,
            LocalDate.now(),
            300.0.toBigDecimal()
        )
    ),
    no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.ÅrsakInnsending.ENDRING,
    true
)
