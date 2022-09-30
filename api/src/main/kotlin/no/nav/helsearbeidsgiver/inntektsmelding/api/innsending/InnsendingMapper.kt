@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.ktor.http.HttpStatusCode
import no.nav.helsearbeidsgiver.felles.Behov
import no.nav.helsearbeidsgiver.felles.Løsning
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.FeilmeldingConstraint
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.IdentitetsnummerConstraint
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.OrganisasjonsnummerConstraint
import org.valiktor.ConstraintViolation
import org.valiktor.ConstraintViolationException
import org.valiktor.DefaultConstraintViolation

class InnsendingMapper(val uuid: String, var resultat: Resultat) {

    fun hasErrors(): Boolean {
        return resultat.løsninger.any { it.error != null }
    }

    fun getConstraintViolations(): List<ConstraintViolation> {
        return resultat.løsninger.filter { it.error != null && !it.error!!.melding.isBlank() }.map { mapConstraint(it) }
    }

    fun mapConstraint(løsning: Løsning): ConstraintViolation {
        val behov = løsning.behov
        if (behov.equals(Behov.VIRKSOMHET.name)) {
            return DefaultConstraintViolation("orgnrUnderenhet", løsning.error!!.melding, OrganisasjonsnummerConstraint())
        }
        if (behov.equals(Behov.FULLT_NAVN.name)) {
            return DefaultConstraintViolation("identitetsnummer", løsning.error!!.melding, IdentitetsnummerConstraint())
        }
        return DefaultConstraintViolation("ukjent", løsning.error!!.melding, FeilmeldingConstraint())
    }

    fun getResponse(): Any {
        if (hasErrors()) {
            throw ConstraintViolationException(getConstraintViolations().toSet())
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
