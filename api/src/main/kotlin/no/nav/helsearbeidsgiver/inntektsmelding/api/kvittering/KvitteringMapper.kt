package no.nav.helsearbeidsgiver.inntektsmelding.api.kvittering

import io.ktor.http.HttpStatusCode
import no.nav.helsearbeidsgiver.felles.Løsning
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.mapper.ResultatMapper
import org.valiktor.ConstraintViolation

class KvitteringMapper(resultat: Resultat) : ResultatMapper<String>(resultat) {
    override fun mapConstraint(løsning: Løsning): ConstraintViolation {
        TODO("Not yet implemented")
    }

    override fun getResultatResponse(): String {
        return resultat.HENT_PERSISTERT_IM?.value.toString()
    }

    override fun getStatus(): HttpStatusCode {
        if (hasErrors()) {
            return HttpStatusCode.InternalServerError
        } else if (getResultatResponse().isEmpty()) {
            return HttpStatusCode.NotFound
        }
        return HttpStatusCode.OK
    }
}
