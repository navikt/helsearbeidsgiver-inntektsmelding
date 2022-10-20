@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.LocalDateSerializer
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isBefore
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isValidBehandlingsdager
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isValidIdentitetsnummer
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isValidOrganisasjonsnummer
import org.valiktor.functions.isGreaterThan
import org.valiktor.functions.isLessThan
import org.valiktor.functions.isNotNull
import org.valiktor.functions.isTrue
import org.valiktor.validate
import java.time.LocalDate

@Serializable
data class InnsendingRequest(
    val orgnrUnderenhet: String,
    val identitetsnummer: String,
    val behandlingsdagerFom: LocalDate,
    val behandlingsdagerTom: LocalDate,
    val behandlingsdager: List<LocalDate>?,
    val egenmeldinger: List<Egenmelding>?,
    val bruttonInntekt: Double,
    val bruttoBekreftet: Boolean,
    val utbetalerFull: Boolean,
    val begrunnelseRedusert: BegrunnelseIngenEllerRedusertUtbetalingKode?,
    val utbetalerHeleEllerDeler: Boolean,
    val refusjonPrMnd: Double?,
    val opphørerKravet: Boolean?,
    val opphørSisteDag: LocalDate?,
    val naturalytelser: List<Naturalytelse>,
    val bekreftOpplysninger: Boolean
) {
    fun validate() {
        validate(this) {
            // Den ansatte
            validate(InnsendingRequest::orgnrUnderenhet).isValidOrganisasjonsnummer()
            // Arbeidsgiver
            validate(InnsendingRequest::identitetsnummer).isValidIdentitetsnummer()
            // Fraværsperiode
            validate(InnsendingRequest::behandlingsdagerFom).isNotNull()
            validate(InnsendingRequest::behandlingsdagerTom).isNotNull()
            validate(InnsendingRequest::behandlingsdagerFom).isBefore(behandlingsdagerTom)
            validate(InnsendingRequest::behandlingsdager).isValidBehandlingsdager() // Velg behandlingsdager
            // Egenmelding
            egenmeldinger?.forEach { it.validate() }
            // Brutto inntekt
            validate(InnsendingRequest::bruttonInntekt).isGreaterThan(0.0)
            validate(InnsendingRequest::bruttonInntekt).isLessThan(1000000.0)
            validate(InnsendingRequest::bruttoBekreftet).isTrue()
            // Refusjon til arbeidsgiver
            if (!utbetalerFull) {
                validate(InnsendingRequest::begrunnelseRedusert).isNotNull()
            }
            if (utbetalerHeleEllerDeler) {
                validate(InnsendingRequest::refusjonPrMnd).isGreaterThan(0.0)
                validate(InnsendingRequest::refusjonPrMnd).isLessThan(1000000.0)
                validate(InnsendingRequest::opphørerKravet).isNotNull()
                if (opphørerKravet!!) {
                    validate(InnsendingRequest::opphørSisteDag).isNotNull()
                }
            }
            // Naturalytelser
            naturalytelser.forEach { it.validate() }
            validate(InnsendingRequest::bekreftOpplysninger).isTrue()
        }
    }
}
