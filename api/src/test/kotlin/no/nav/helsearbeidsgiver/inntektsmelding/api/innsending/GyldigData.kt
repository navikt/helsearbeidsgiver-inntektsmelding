package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import no.nav.helsearbeidsgiver.felles.Periode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.Naturalytelse
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.NaturalytelseKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.ÅrsakInnsending
import no.nav.helsearbeidsgiver.felles.inntektsmelding.request.FullLønnIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.felles.inntektsmelding.request.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.inntektsmelding.request.Inntekt
import no.nav.helsearbeidsgiver.felles.inntektsmelding.request.Refusjon
import no.nav.helsearbeidsgiver.inntektsmelding.api.TestData
import java.time.LocalDate

val GYLDIG = InnsendingRequest(
    TestData.validOrgNr,
    TestData.validIdentitetsnummer,
    listOf(LocalDate.now().plusDays(5)),
    listOf(Periode(LocalDate.now(), LocalDate.now().plusDays(2))),
    emptyList(),
    LocalDate.now(),
    emptyList(),
    Inntekt(true, 32100.0, endringÅrsak = null, false),
    FullLønnIArbeidsgiverPerioden(true, BegrunnelseIngenEllerRedusertUtbetalingKode.ArbeidOpphoert),
    Refusjon(true, 200.0, LocalDate.now()),
    listOf(Naturalytelse(NaturalytelseKode.KostDoegn, LocalDate.now(), 300.0)),
    ÅrsakInnsending.Endring,
    true
)
