package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import no.nav.helsearbeidsgiver.felles.Løsning
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.mapper.ResultatMapper
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.FeilmeldingConstraint
import org.valiktor.ConstraintViolation
import org.valiktor.DefaultConstraintViolation

class InntektMapper(resultat: Resultat) : ResultatMapper<InntektResponse>(resultat) {

    override fun mapConstraint(løsning: Løsning): ConstraintViolation {
        return DefaultConstraintViolation("ukjent", løsning.error!!.melding, FeilmeldingConstraint)
    }

    override fun getResultatResponse(): InntektResponse {
        return InntektResponse(resultat.INNTEKT!!.value!!.gjennomsnitt(), resultat.INNTEKT!!.value!!.historisk)
    }
}
