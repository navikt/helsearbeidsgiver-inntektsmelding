@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import io.ktor.http.HttpStatusCode
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Løsning
import no.nav.helsearbeidsgiver.felles.MottattPeriode
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.PreutfyltResponse
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.FeilmeldingConstraint
import org.valiktor.ConstraintViolation
import org.valiktor.ConstraintViolationException
import org.valiktor.DefaultConstraintViolation

class PreutfyltMapper(val uuid: String, val resultat: Resultat, val request: PreutfyltRequest) {

    fun hasErrors(): Boolean {
        return findAll().any { it.error != null }
    }

    fun findAll(): List<Løsning> {
        return listOf(resultat.FULLT_NAVN, resultat.VIRKSOMHET, resultat.ARBEIDSFORHOLD, resultat.SYK, resultat.INNTEKT, resultat.EGENMELDING).filterNotNull()
    }

    fun getConstraintViolations(): List<ConstraintViolation> {
        return findAll()
            .filter { it.error != null && !it.error!!.melding.isBlank() }
            .map { mapConstraint(it) }
    }

    fun mapConstraint(løsning: Løsning): ConstraintViolation {
        if (løsning is VirksomhetLøsning) {
            return DefaultConstraintViolation(Key.ORGNRUNDERENHET.str, løsning.error?.melding ?: "Ukjent feil", FeilmeldingConstraint)
        }
        if (løsning is NavnLøsning) {
            return DefaultConstraintViolation("identitetsnummer", løsning.error?.melding ?: "Ukjent feil", FeilmeldingConstraint)
        }
        return DefaultConstraintViolation("ukjent", løsning.error?.melding ?: "Ukjent feil", FeilmeldingConstraint)
    }

    fun mapEgenmeldingsperioder(): List<MottattPeriode> {
        val egenmelding = resultat.EGENMELDING
        sikkerlogg.info("Fant egenmelding for $uuid")
        return egenmelding?.value ?: emptyList()
    }

    fun mapBehandlingsperiode(): MottattPeriode {
        val syk = resultat.SYK
        sikkerlogg.info("Fant behandlingsperiode $syk for $uuid")
        return syk?.value?.behandlingsperiode!!
    }

    fun mapFraværsperiode(): List<MottattPeriode> {
        val syk = resultat.SYK
        sikkerlogg.info("Fant fraværsperiode $syk for $uuid")
        return syk?.value?.fravaersperiode ?: emptyList()
    }

    fun mapFulltNavn(): String {
        return resultat.FULLT_NAVN?.value ?: "Mangler navn"
    }

    fun mapInntekt(): Inntekt {
        return resultat.INNTEKT?.value!!
    }

    fun getResponse(): PreutfyltResponse {
        if (hasErrors()) {
            throw ConstraintViolationException(getConstraintViolations().toSet())
        }
        val inntekt = mapInntekt()
        return PreutfyltResponse(
            navn = mapFulltNavn(),
            identitetsnummer = request.identitetsnummer,
            orgnrUnderenhet = request.orgnrUnderenhet,
            fravaersperioder = mapFraværsperiode(),
            egenmeldingsperioder = mapEgenmeldingsperioder(),
            bruttoinntekt = inntekt.bruttoInntekt,
            tidligereinntekter = inntekt.historisk,
            behandlingsperiode = mapBehandlingsperiode()
        )
    }

    fun getStatus(): HttpStatusCode {
        if (hasErrors()) {
            return HttpStatusCode.InternalServerError
        }
        return HttpStatusCode.Created
    }
}
