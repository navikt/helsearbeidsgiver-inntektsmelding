package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import java.time.ZonedDateTime

fun mapInntektsmeldingDokument(request: InnsendingRequest, fulltNavn: String, arbeidsgiver: String): InntektsmeldingDokument {
    try {
        return InntektsmeldingDokument(
            request.orgnrUnderenhet,
            request.identitetsnummer,
            fulltNavn,
            arbeidsgiver,
            request.behandlingsdager,
            request.egenmeldingsperioder,
            request.bestemmendeFraværsdag,
            request.fraværsperioder,
            request.arbeidsgiverperioder,
            request.inntekt.beregnetInntekt,
            request.inntekt,
            request.fullLønnIArbeidsgiverPerioden,
            request.refusjon,
            request.naturalytelser,
            ZonedDateTime.now().toOffsetDateTime(),
            request.årsakInnsending,
            "" // TODO Mangler innsenders fødselsnr
        )
    } catch (ex: Exception) {
        throw UgyldigFormatException(ex)
    }
}

class UgyldigFormatException(ex: Exception) : Exception(ex)
