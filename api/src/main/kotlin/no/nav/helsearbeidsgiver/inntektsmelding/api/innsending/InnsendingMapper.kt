@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Løsning
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Inntekt
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.KvitteringResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.mapper.ResultatMapper
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.FeilmeldingConstraint
import org.valiktor.ConstraintViolation
import org.valiktor.DefaultConstraintViolation
import java.util.UUID

// TODO slett
class InnsendingMapper(val uuid: UUID, resultat: Resultat) : ResultatMapper<InnsendingResponse>(resultat) {

    override fun mapConstraint(løsning: Løsning): ConstraintViolation {
        if (løsning is VirksomhetLøsning) {
            return DefaultConstraintViolation(DataFelt.ORGNRUNDERENHET.str, løsning.error!!.melding, FeilmeldingConstraint)
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

fun mapInnsending(inntektsmeldingDokument: InntektsmeldingDokument): KvitteringResponse {
    return KvitteringResponse(
        orgnrUnderenhet = inntektsmeldingDokument.orgnrUnderenhet,
        identitetsnummer = inntektsmeldingDokument.identitetsnummer,
        fulltNavn = inntektsmeldingDokument.fulltNavn,
        virksomhetNavn = inntektsmeldingDokument.virksomhetNavn,
        behandlingsdager = inntektsmeldingDokument.behandlingsdager,
        egenmeldingsperioder = inntektsmeldingDokument.egenmeldingsperioder,
        arbeidsgiverperioder = inntektsmeldingDokument.arbeidsgiverperioder,
        bestemmendeFraværsdag = inntektsmeldingDokument.bestemmendeFraværsdag,
        fraværsperioder = inntektsmeldingDokument.fraværsperioder,
        inntekt = Inntekt(
            true,
            // Kan slette nullable inntekt og fallback når IM med gammelt format slettes fra database
            inntektsmeldingDokument.inntekt?.beregnetInntekt ?: inntektsmeldingDokument.beregnetInntekt,
            inntektsmeldingDokument.inntekt?.endringÅrsak,
            inntektsmeldingDokument.inntekt?.manueltKorrigert ?: false
        ),
        fullLønnIArbeidsgiverPerioden = inntektsmeldingDokument.fullLønnIArbeidsgiverPerioden,
        refusjon = inntektsmeldingDokument.refusjon,
        naturalytelser = inntektsmeldingDokument.naturalytelser,
        årsakInnsending = inntektsmeldingDokument.årsakInnsending,
        bekreftOpplysninger = true,
        tidspunkt = inntektsmeldingDokument.tidspunkt
    )
}
