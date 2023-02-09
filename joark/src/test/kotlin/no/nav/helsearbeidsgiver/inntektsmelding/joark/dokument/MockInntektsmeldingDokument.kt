package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.felles.inntektsmelding.db.FullLønnIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.felles.inntektsmelding.db.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.inntektsmelding.db.Refusjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.Naturalytelse
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.NaturalytelseKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.Periode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.RefusjonEndring
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.ÅrsakBeregnetInntektEndringKodeliste
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.ÅrsakInnsending
import java.time.LocalDate
import java.time.LocalDateTime

fun MockInntektsmeldingDokument(dag: LocalDate = LocalDate.of(2022, 12, 24)): InntektsmeldingDokument = InntektsmeldingDokument(
    orgnrUnderenhet = "123456789",
    identitetsnummer = "12345678901",
    fulltNavn = "Ola Normann",
    virksomhetNavn = "Norge AS",
    behandlingsdager = listOf(dag),
    egenmeldingsperioder = listOf(
        Periode(dag, dag.plusDays(2)),
        Periode(dag.plusDays(3), dag.plusDays(4))
    ),
    beregnetInntektEndringÅrsak = ÅrsakBeregnetInntektEndringKodeliste.FeilInntekt,
    beregnetInntekt = 25000.0,
    fullLønnIArbeidsgiverPerioden = FullLønnIArbeidsgiverPerioden(true, begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.BeskjedGittForSent),
    refusjon = Refusjon(
        25000.0,
        dag.plusDays(3),
        listOf(
            RefusjonEndring(140.0, dag.minusDays(4)),
            RefusjonEndring(150.0, dag.minusDays(5)),
            RefusjonEndring(160.0, dag.minusDays(6))
        )
    ),
    naturalytelser = listOf(
        Naturalytelse(NaturalytelseKode.Bil, dag.plusDays(5), 350.0),
        Naturalytelse(NaturalytelseKode.Bil, dag.plusDays(5), 350.0)
    ),
    fraværsperioder = listOf(
        Periode(dag, dag.plusDays(55)),
        Periode(dag, dag.plusDays(22)),
        Periode(dag, dag.plusDays(32))
    ),
    arbeidsgiverperioder = listOf(
        Periode(dag, dag.plusDays(30)),
        Periode(dag, dag.plusDays(40)),
        Periode(dag, dag.plusDays(40))
    ),
    bestemmendeFraværsdag = dag.plusDays(90),
    tidspunkt = LocalDateTime.now(),
    årsakInnsending = ÅrsakInnsending.Ny,
    identitetsnummerInnsender = "123123123123123"
)
