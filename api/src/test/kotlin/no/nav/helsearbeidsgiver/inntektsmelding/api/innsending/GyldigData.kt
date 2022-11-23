package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

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
    Bruttoinntekt(true, 32100.0, endringaarsak = null, false),
    FullLønnIArbeidsgiverPerioden(true, BegrunnelseIngenEllerRedusertUtbetalingKode.ArbeidOpphoert),
    HeleEllerdeler(true, 200.0, LocalDate.now()),
    listOf(Naturalytelse(NaturalytelseKode.kostDoegn, LocalDate.now(), 300.0)),
    true
)
