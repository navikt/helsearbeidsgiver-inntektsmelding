@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.mapper

import io.ktor.http.HttpStatusCode
import no.nav.helsearbeidsgiver.felles.Løsning
import no.nav.helsearbeidsgiver.felles.Resultat
import org.valiktor.ConstraintViolation
import org.valiktor.ConstraintViolationException

abstract class ResultatMapper<T>(val resultat: Resultat) {

    fun hasErrors(): Boolean {
        return findAll().any { it.error != null }
    }

    private fun findAll(): List<Løsning> {
        return listOfNotNull(
            resultat.FULLT_NAVN,
            resultat.VIRKSOMHET
        )
    }

    fun getConstraintViolations(): List<ConstraintViolation> {
        return findAll()
            .filter { !it.error?.melding.isNullOrBlank() }
            .map { mapConstraint(it) }
    }

    abstract fun mapConstraint(løsning: Løsning): ConstraintViolation

    fun getStatus(): HttpStatusCode {
        if (hasErrors()) {
            return HttpStatusCode.InternalServerError
        }
        return HttpStatusCode.Created
    }

    fun getResponse(): T {
        if (hasErrors()) {
            throw ConstraintViolationException(getConstraintViolations().toSet())
        }
        return getResultatResponse()
    }

    protected abstract fun getResultatResponse(): T
}
