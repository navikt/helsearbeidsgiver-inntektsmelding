@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Løsning
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Inntekt
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.inntektsmelding.api.mapper.ResultatMapper
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.FeilmeldingConstraint
import org.valiktor.ConstraintViolation
import org.valiktor.DefaultConstraintViolation

class InnsendingMapper(val uuid: String, resultat: Resultat) : ResultatMapper<InnsendingResponse>(resultat) {

    override fun mapConstraint(løsning: Løsning): ConstraintViolation {
        if (løsning is VirksomhetLøsning) {
            return DefaultConstraintViolation(Key.ORGNRUNDERENHET.str, løsning.error!!.melding, FeilmeldingConstraint)
        }
        if (løsning is NavnLøsning) {
            return DefaultConstraintViolation("identitetsnummer", løsning.error!!.melding, FeilmeldingConstraint)
        }
        return DefaultConstraintViolation("ukjent", løsning.error!!.melding, FeilmeldingConstraint)
    }

    override fun getResultatResponse(): InnsendingResponse {
        return InnsendingResponse(uuid)
    }
}

fun mapInnsending(inntektsmeldingDokument: InntektsmeldingDokument): InnsendingRequest {
    return InnsendingRequest(
        orgnrUnderenhet = inntektsmeldingDokument.orgnrUnderenhet,
        identitetsnummer = inntektsmeldingDokument.identitetsnummer,
        behandlingsdager = inntektsmeldingDokument.behandlingsdager,
        egenmeldingsperioder = inntektsmeldingDokument.egenmeldingsperioder,
        arbeidsgiverperioder = inntektsmeldingDokument.arbeidsgiverperioder,
        bestemmendeFraværsdag = inntektsmeldingDokument.bestemmendeFraværsdag,
        fraværsperioder = inntektsmeldingDokument.fraværsperioder,
        // @TODO det er noe informasjon som vi mangler på InntektsMeldingDokument
        inntekt = Inntekt(true, inntektsmeldingDokument.beregnetInntekt, null, false),
        fullLønnIArbeidsgiverPerioden = inntektsmeldingDokument.fullLønnIArbeidsgiverPerioden,
        refusjon = inntektsmeldingDokument.refusjon,
        naturalytelser = inntektsmeldingDokument.naturalytelser,
        årsakInnsending = inntektsmeldingDokument.årsakInnsending,
        bekreftOpplysninger = true
    )
}
