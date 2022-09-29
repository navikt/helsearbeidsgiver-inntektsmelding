package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.ktor.http.HttpStatusCode
import no.nav.helsearbeidsgiver.felles.Resultat

class InnsendingMapper(val uuid: String, var resultat: Resultat) {

    fun hasErrors(): Boolean {
        return resultat.løsninger.any { it.error != null }
    }

    fun getResponse(): Any {
        if (hasErrors()) {
            return InnsendingFeilet(uuid, resultat.løsninger.filter { it.error != null }.first().error!!.melding)
        }
        return InnsendingResponse(uuid)
    }

    fun getStatus(): HttpStatusCode {
        if (hasErrors()) {
            return HttpStatusCode.InternalServerError
        }
        return HttpStatusCode.Created
    }
}
