package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import org.valiktor.ConstraintViolation
import org.valiktor.i18n.mapToMessage
import java.util.Locale

fun validationResponseMapper(violations: Set<ConstraintViolation>): ValidationResponse =
    ValidationResponse(
        violations
            .mapToMessage(baseName = "messages", locale = Locale.forLanguageTag("no"))
            .map {
                ValidationError(it.property, it.message, it.value.toString())
            },
    )
