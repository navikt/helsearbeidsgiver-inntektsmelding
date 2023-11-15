package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.domene.inntektsmelding.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Inntektsmelding
import java.time.ZonedDateTime

fun mapInntektsmelding(
    request: Innsending,
    fulltnavnArbeidstaker: String,
    arbeidsgiver: String,
    innsenderNavn: String
): Inntektsmelding =
    try {
        Inntektsmelding(
            orgnrUnderenhet = request.orgnrUnderenhet,
            identitetsnummer = request.identitetsnummer,
            fulltNavn = fulltnavnArbeidstaker,
            virksomhetNavn = arbeidsgiver,
            behandlingsdager = request.behandlingsdager,
            egenmeldingsperioder = request.egenmeldingsperioder,
            bestemmendeFraværsdag = request.bestemmendeFraværsdag,
            fraværsperioder = request.fraværsperioder,
            arbeidsgiverperioder = request.arbeidsgiverperioder,
            beregnetInntekt = request.inntekt.beregnetInntekt,
            inntekt = request.inntekt,
            fullLønnIArbeidsgiverPerioden = request.fullLønnIArbeidsgiverPerioden,
            refusjon = request.refusjon,
            naturalytelser = request.naturalytelser,
            tidspunkt = ZonedDateTime.now().toOffsetDateTime(),
            årsakInnsending = request.årsakInnsending,
            innsenderNavn = innsenderNavn,
            forespurtData = request.forespurtData,
            telefonnummer = request.telefonnummer
        )
    } catch (ex: Exception) {
        throw UgyldigFormatException(ex)
    }

class UgyldigFormatException(ex: Exception) : Exception(ex)

fun Inntektsmelding.erDuplikatAv(other: Inntektsmelding): Boolean =
    this == other.copy(tidspunkt = tidspunkt)
