package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import org.valiktor.ConstraintViolation
import org.valiktor.i18n.mapToMessage
import java.util.Locale

fun validationResponseMapper(violations: Set<ConstraintViolation>): ValidationResponse {
    return ValidationResponse(
        violations
            .mapToMessage(baseName = "messages", locale = Locale.ENGLISH)
            .map { ValidationError(it.property, it.message) }
    )
}
