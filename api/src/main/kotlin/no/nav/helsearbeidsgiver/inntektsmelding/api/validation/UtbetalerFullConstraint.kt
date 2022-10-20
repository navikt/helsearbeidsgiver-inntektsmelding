package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import no.nav.helsearbeidsgiver.inntektsmelding.api.innsending.BegrunnelseIngenEllerRedusertUtbetalingKode
import org.valiktor.Validator

class UtbetalerFullConstraint : CustomConstraint
fun <E> Validator<E>.Property<Boolean?>.isUbetalerFull(begrunnelseRedusert: BegrunnelseIngenEllerRedusertUtbetalingKode?) =
    this.validate(UtbetalerFullConstraint()) { it == true || (it == false && (begrunnelseRedusert != null)) }
