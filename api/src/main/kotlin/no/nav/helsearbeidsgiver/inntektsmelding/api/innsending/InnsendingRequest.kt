@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.LocalDateSerializer
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isBefore
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isBekreftetInntekt
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isBekreftetOpplysninger
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isOpphørerValid
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isUbetalerFull
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isUtbetalerHele
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isValidBehandlingsdager
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isValidBrutto
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isValidEgenmeldinger
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isValidIdentitetsnummer
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isValidNaturalytelser
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.isValidOrganisasjonsnummer
import org.valiktor.functions.isNotNull
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
    val utbetalerHele: Boolean,
    val refusjonPrMnd: Double?,
    val opphørerKravet: Boolean,
    val opphørSisteDag: LocalDate?,
    val naturalytelser: List<Naturalytelse>,
    val bekreftOpplysninger: Boolean
) {
    fun validate() {
        validate(this) {
            validate(InnsendingRequest::orgnrUnderenhet).isValidOrganisasjonsnummer()
            validate(InnsendingRequest::identitetsnummer).isValidIdentitetsnummer()
            validate(InnsendingRequest::behandlingsdagerFom).isNotNull()
            validate(InnsendingRequest::behandlingsdagerTom).isNotNull()
            validate(InnsendingRequest::behandlingsdagerFom).isBefore(behandlingsdagerTom)
            validate(InnsendingRequest::behandlingsdager).isValidBehandlingsdager()
            validate(InnsendingRequest::egenmeldinger).isValidEgenmeldinger()
            validate(InnsendingRequest::bruttonInntekt).isValidBrutto()

            validate(InnsendingRequest::bruttoBekreftet).isBekreftetInntekt()
            validate(InnsendingRequest::opphørerKravet).isOpphørerValid(opphørSisteDag)

            validate(InnsendingRequest::utbetalerFull).isUbetalerFull(begrunnelseRedusert)
            validate(InnsendingRequest::utbetalerHele).isUtbetalerHele(refusjonPrMnd)
            validate(InnsendingRequest::naturalytelser).isValidNaturalytelser()
            validate(InnsendingRequest::bekreftOpplysninger).isBekreftetOpplysninger()
        }
    }
}
