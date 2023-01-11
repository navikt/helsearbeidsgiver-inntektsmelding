@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Løsning
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.Periode
import no.nav.helsearbeidsgiver.felles.PreutfyltResponse
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import no.nav.helsearbeidsgiver.inntektsmelding.api.mapper.ResultatMapper
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.FeilmeldingConstraint
import org.valiktor.ConstraintViolation
import org.valiktor.DefaultConstraintViolation

class PreutfyltMapper(
    val uuid: String,
    resultat: Resultat,
    val request: PreutfyltRequest,
    var sykemeldingsperioder: List<Periode>,
    var egenmeldingsperioder: List<Periode>
) : ResultatMapper<PreutfyltResponse>(resultat) {

    override fun mapConstraint(løsning: Løsning): ConstraintViolation {
        if (løsning is VirksomhetLøsning) {
            return DefaultConstraintViolation(Key.ORGNRUNDERENHET.str, løsning.error?.melding ?: "Ukjent feil", FeilmeldingConstraint)
        }
        if (løsning is NavnLøsning) {
            return DefaultConstraintViolation("identitetsnummer", løsning.error?.melding ?: "Ukjent feil", FeilmeldingConstraint)
        }
        return DefaultConstraintViolation("ukjent", løsning.error?.melding ?: "Ukjent feil", FeilmeldingConstraint)
    }

    fun mapEgenmeldingsperioder(): List<Periode> {
        val trenger = resultat.HENT_TRENGER_IM
        sikkerlogg.info("Fant egenmeldingsperioder: $trenger for $uuid")
        return trenger?.value?.egenmeldingsperioder ?: egenmeldingsperioder
    }

    fun mapFraværsperiode(): List<Periode> {
        val trenger = resultat.HENT_TRENGER_IM
        sikkerlogg.info("Fant fraværsperiode data: $trenger for $uuid")
        return trenger?.value?.sykemeldingsperioder ?: sykemeldingsperioder
    }

    fun mapFulltNavn(): String {
        return resultat.FULLT_NAVN?.value ?: "Mangler navn"
    }

    fun mapInntekt(): Inntekt {
        return resultat.INNTEKT?.value!!
    }

    override fun getResultatResponse(): PreutfyltResponse {
        val inntekt = mapInntekt()
        return PreutfyltResponse(
            navn = mapFulltNavn(),
            identitetsnummer = request.identitetsnummer,
            orgnrUnderenhet = request.orgnrUnderenhet,
            fravaersperioder = mapFraværsperiode(),
            egenmeldingsperioder = mapEgenmeldingsperioder(),
            bruttoinntekt = inntekt.bruttoInntekt,
            tidligereinntekter = inntekt.historisk,
            behandlingsperiode = null,
            behandlingsdager = emptyList()
        )
    }
}
