@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.felles.inntektsmelding.db.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.inntektsmelding.request.InnsendingRequest
import java.time.LocalDateTime

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
            request.fullLønnIArbeidsgiverPerioden,
            request.refusjon,
            request.naturalytelser,
            LocalDateTime.now(),
            request.årsakInnsending,
            "" // TODO Mangler innsenders fødselsnr
        )
    } catch (ex: Exception) {
        throw UgyldigFormatException(ex)
    }
}

class UgyldigFormatException(ex: Exception) : Exception(ex)
