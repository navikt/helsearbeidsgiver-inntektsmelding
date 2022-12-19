@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Løsning
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
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
