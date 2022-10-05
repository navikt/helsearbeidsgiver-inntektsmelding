@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import io.ktor.http.HttpStatusCode
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Løsning
import no.nav.helsearbeidsgiver.inntektsmelding.api.dto.Inntekt
import no.nav.helsearbeidsgiver.inntektsmelding.api.dto.MottattArbeidsforhold
import no.nav.helsearbeidsgiver.inntektsmelding.api.dto.MottattHistoriskInntekt
import no.nav.helsearbeidsgiver.inntektsmelding.api.dto.MottattPeriode
import no.nav.helsearbeidsgiver.inntektsmelding.api.dto.PreutfyltResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.FeilmeldingConstraint
import org.valiktor.ConstraintViolation
import org.valiktor.ConstraintViolationException
import org.valiktor.DefaultConstraintViolation
import java.time.LocalDate

class PreutfyltMapper(val uuid: String, var resultat: PreutfyltResultat, val request: PreutfyllRequest) {

    fun hasErrors(): Boolean {
        return findAll().any { it.error != null }
    }

    fun findAll(): List<Løsning> {
        return listOf(resultat.FULLT_NAVN, resultat.VIRKSOMHET, resultat.ARBEIDSFORHOLD, resultat.SYK, resultat.INNTEKT).filter { it != null } as List<Løsning>
    }

    fun getConstraintViolations(): List<ConstraintViolation> {
        return findAll()
            .filter { it.error != null && !it.error!!.melding.isBlank() }
            .map { mapConstraint(it) }
    }

    fun mapConstraint(løsning: Løsning): ConstraintViolation {
        val behov = løsning.behovType
        if (behov == BehovType.VIRKSOMHET) {
            return DefaultConstraintViolation("orgnrUnderenhet", løsning.error!!.melding, FeilmeldingConstraint())
        }
        if (behov == BehovType.FULLT_NAVN) {
            return DefaultConstraintViolation("identitetsnummer", løsning.error!!.melding, FeilmeldingConstraint())
        }
        return DefaultConstraintViolation("ukjent", løsning.error!!.melding, FeilmeldingConstraint())
    }

    fun mapEgenmeldingsperioder(): List<MottattPeriode> {
        return listOf(MottattPeriode(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 1, 2)))
    }

    fun mapBehandlingsperiode(): MottattPeriode {
        val syk = resultat.SYK
        sikkerlogg.info("Fant behandlingsperiode $syk for $uuid")
        return MottattPeriode(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 1, 2))
    }

    fun mapArbeidsforhold(): List<MottattArbeidsforhold> {
        val arbeidsforhold = resultat.ARBEIDSFORHOLD
        sikkerlogg.info("Fant arbeidsforhold $arbeidsforhold for $uuid")
        return listOf(MottattArbeidsforhold("arbeidsforhold1", "test", 100.0f))
    }

    fun mapFraværsperiode(): MutableMap<String, List<MottattPeriode>> {
        val syk = resultat.SYK
        sikkerlogg.info("Fant fraværsperiode $syk for $uuid")
        val map = mutableMapOf<String, List<MottattPeriode>>()
        map.put("arbeidsforhold1", listOf(MottattPeriode(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 1, 1))))
        map.put("arbeidsforhold2", listOf(MottattPeriode(LocalDate.of(2022, 1, 2), LocalDate.of(2022, 1, 2))))
        map.put("arbeidsforhold3", listOf(MottattPeriode(LocalDate.of(2022, 1, 3), LocalDate.of(2022, 1, 3))))
        return map
    }

    fun mapFulltNavn(): String {
        return resultat.FULLT_NAVN?.value as String
    }

    fun mapVirksomhet(): String {
        return resultat.VIRKSOMHET?.value as String
    }

    fun mapInntekt(): Inntekt {
        sikkerlogg.info("Fant inntekt ${resultat.INNTEKT} for $uuid")
        return Inntekt(250000, listOf(MottattHistoriskInntekt("Januar", 32000)))
    }

    fun getResponse(): PreutfyltResponse {
        if (hasErrors()) {
            throw ConstraintViolationException(getConstraintViolations().toSet())
        }
        val inntekt = mapInntekt()
        return PreutfyltResponse(
            navn = mapFulltNavn(),
            identitetsnummer = request.identitetsnummer,
            virksomhetsnavn = mapVirksomhet(),
            orgnrUnderenhet = request.orgnrUnderenhet,
            fravaersperiode = mapFraværsperiode(),
            egenmeldingsperioder = mapEgenmeldingsperioder(),
            bruttoinntekt = inntekt.bruttoInntekt,
            tidligereinntekt = inntekt.historisk,
            behandlingsperiode = mapBehandlingsperiode(),
            arbeidsforhold = mapArbeidsforhold()
        )
    }

    fun getStatus(): HttpStatusCode {
        if (hasErrors()) {
            return HttpStatusCode.InternalServerError
        }
        return HttpStatusCode.Created
    }
}
