package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import no.nav.helsearbeidsgiver.felles.Løsning
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.mapper.ResultatMapper
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.FeilmeldingConstraint
import org.valiktor.ConstraintViolation
import org.valiktor.ConstraintViolationException
import org.valiktor.DefaultConstraintViolation

class TrengerMapper(val uuid: String, resultat: Resultat, val request: TrengerRequest) : ResultatMapper<TrengerInntektResponse>(resultat) {

    override fun mapConstraint(løsning: Løsning): ConstraintViolation {
        return DefaultConstraintViolation("ukjent", løsning.error?.melding ?: "Ukjent feil", FeilmeldingConstraint)
    }

    override fun getResultatResponse(): TrengerInntektResponse {
        val spleisForespoersel = resultat.HENT_TRENGER_IM?.value
        if (hasErrors() || spleisForespoersel == null) {
            throw ConstraintViolationException(getConstraintViolations().toSet())
        }
        return TrengerInntektResponse(
            uuid = request.uuid,
            fnr = spleisForespoersel.fnr,
            orgnr = spleisForespoersel.orgnr
        )
    }
}
