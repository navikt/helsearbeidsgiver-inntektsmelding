package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import no.nav.helsearbeidsgiver.inntektsmelding.api.TestData
import java.time.LocalDate

val GYLDIG = InnsendingRequest(
    TestData.validOrgNr,
    TestData.validIdentitetsnummer,
    LocalDate.now(),
    LocalDate.now().plusDays(12),
    listOf(LocalDate.now()),
    listOf(Egenmelding(LocalDate.now(), LocalDate.now().plusDays(2))),
    32100.0,
    true,
    true,
    BegrunnelseIngenEllerRedusertUtbetalingKode.ArbeidOpphoert,
    true,
    2500.0,
    true,
    LocalDate.now(),
    listOf(Naturalytelse(NaturalytelseKode.kostDoegn, LocalDate.now(), 300.0)),
    true
)
