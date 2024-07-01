package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding

fun Inntektsmelding.erDuplikatAv(other: Inntektsmelding): Boolean =
    this == other.copy(
        vedtaksperiodeId = vedtaksperiodeId,
        tidspunkt = tidspunkt,
        årsakInnsending = årsakInnsending,
        innsenderNavn = innsenderNavn
    )

fun Inntektsmelding.erDuplikatAv(other: Innsending): Boolean =
    this.orgnrUnderenhet == other.orgnrUnderenhet &&
        this.identitetsnummer == other.identitetsnummer &&
        this.behandlingsdager == other.behandlingsdager &&
        this.egenmeldingsperioder == other.egenmeldingsperioder &&
        this.arbeidsgiverperioder == other.arbeidsgiverperioder &&
        this.bestemmendeFraværsdag == other.bestemmendeFraværsdag &&
        this.fraværsperioder == other.fraværsperioder &&
        this.inntekt == other.inntekt &&
        this.fullLønnIArbeidsgiverPerioden == other.fullLønnIArbeidsgiverPerioden &&
        this.refusjon == other.refusjon &&
        this.naturalytelser == other.naturalytelser &&
        this.årsakInnsending == other.årsakInnsending &&
        this.telefonnummer == other.telefonnummer &&
        this.forespurtData == other.forespurtData

fun Innsending.erDuplikatAv(other: Innsending): Boolean =
    this == other.copy(
        årsakInnsending = årsakInnsending
    )
