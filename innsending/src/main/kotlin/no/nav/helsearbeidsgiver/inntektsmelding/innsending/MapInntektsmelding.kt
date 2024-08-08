package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.FullLoennIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.bestemmendeFravaersdag
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.ForslagInntekt
import java.time.ZonedDateTime

fun mapInntektsmelding(
    forespoersel: Forespoersel,
    skjema: Innsending,
    fulltnavnArbeidstaker: String,
    virksomhetNavn: String,
    innsenderNavn: String,
): Inntektsmelding {
    val egenmeldingsperioder =
        if (forespoersel.forespurtData.arbeidsgiverperiode.paakrevd) {
            skjema.egenmeldingsperioder
        } else {
            forespoersel.egenmeldingsperioder
        }

    val arbeidsgiverperioder =
        if (forespoersel.forespurtData.arbeidsgiverperiode.paakrevd) {
            skjema.arbeidsgiverperioder
        } else {
            emptyList()
        }

    val fullLoennIArbeidsgiverPerioden =
        if (forespoersel.forespurtData.arbeidsgiverperiode.paakrevd) {
            if (skjema.fullLønnIArbeidsgiverPerioden?.utbetalerFullLønn == false) {
                skjema.fullLønnIArbeidsgiverPerioden
            } else {
                FullLoennIArbeidsgiverPerioden(
                    utbetalerFullLønn = true,
                    begrunnelse = null,
                    utbetalt = null,
                )
            }
        } else {
            null
        }

    val inntektsdato =
        if (forespoersel.forespurtData.arbeidsgiverperiode.paakrevd) {
            // NB!: 'skjema.bestemmendeFraværsdag' inneholder egentlig inntektsdato og ikke bestemmende fraværsdag. Utbedring kommer.
            skjema.bestemmendeFraværsdag
        } else {
            forespoersel.forslagInntektsdato()
        }

    val bestemmendeFravaersdag =
        if (
            forespoersel.forespurtData.arbeidsgiverperiode.paakrevd ||
            (!forespoersel.forespurtData.inntekt.paakrevd && forespoersel.forespurtData.refusjon.paakrevd)
        ) {
            bestemmendeFravaersdag(
                arbeidsgiverperioder = arbeidsgiverperioder,
                sykmeldingsperioder = forespoersel.sykmeldingsperioder,
            )
        } else {
            forespoersel.forslagBestemmendeFravaersdag()
        }

    val inntekt =
        if (forespoersel.forespurtData.inntekt.paakrevd) {
            skjema.inntekt
        } else {
            Inntekt(
                bekreftet = true,
                beregnetInntekt = (forespoersel.forespurtData.inntekt.forslag as ForslagInntekt.Fastsatt).fastsattInntekt,
                endringÅrsak = null,
                manueltKorrigert = true,
            )
        }

    val refusjon =
        if (forespoersel.forespurtData.refusjon.paakrevd && skjema.refusjon.utbetalerHeleEllerDeler) {
            skjema.refusjon
        } else {
            Refusjon(
                utbetalerHeleEllerDeler = false,
                refusjonPrMnd = null,
                refusjonOpphører = null,
                refusjonEndringer = null,
            )
        }

    val forespurtData =
        mapOf(
            "arbeidsgiverperiode" to forespoersel.forespurtData.arbeidsgiverperiode.paakrevd,
            "inntekt" to forespoersel.forespurtData.inntekt.paakrevd,
            "refusjon" to forespoersel.forespurtData.refusjon.paakrevd,
        ).filterValues { it }
            .keys
            .toList()

    return Inntektsmelding(
        vedtaksperiodeId = forespoersel.vedtaksperiodeId,
        orgnrUnderenhet = forespoersel.orgnr,
        identitetsnummer = forespoersel.fnr,
        fulltNavn = fulltnavnArbeidstaker,
        virksomhetNavn = virksomhetNavn,
        behandlingsdager = emptyList(),
        egenmeldingsperioder = egenmeldingsperioder,
        fraværsperioder = forespoersel.sykmeldingsperioder,
        arbeidsgiverperioder = arbeidsgiverperioder,
        beregnetInntekt = inntekt.beregnetInntekt,
        inntektsdato = inntektsdato,
        inntekt = inntekt,
        fullLønnIArbeidsgiverPerioden = fullLoennIArbeidsgiverPerioden,
        refusjon = refusjon,
        naturalytelser = skjema.naturalytelser,
        tidspunkt = ZonedDateTime.now().toOffsetDateTime(),
        årsakInnsending = skjema.årsakInnsending,
        innsenderNavn = innsenderNavn,
        telefonnummer = skjema.telefonnummer,
        forespurtData = forespurtData,
        bestemmendeFraværsdag = bestemmendeFravaersdag,
    )
}
