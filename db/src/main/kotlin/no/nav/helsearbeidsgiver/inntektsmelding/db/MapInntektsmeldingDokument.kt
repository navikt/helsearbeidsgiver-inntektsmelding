package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import java.time.ZonedDateTime

fun mapInntektsmeldingDokument(
    request: InnsendingRequest,
    fulltnavnArbeidstaker: String,
    arbeidsgiver: String,
    innsenderNavn: String
): InntektsmeldingDokument =
    try {
        InntektsmeldingDokument(
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
            // TODO: burde være tidpunkt i vår tidsone OSLO
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
