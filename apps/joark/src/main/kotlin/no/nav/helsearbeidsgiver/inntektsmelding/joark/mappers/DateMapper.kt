package no.nav.helsearbeidsgiver.inntektsmelding.joark.mappers

import org.mapstruct.Mapper
import java.time.LocalDateTime
import java.time.OffsetDateTime

@Mapper
abstract class DateMapper {

    fun offsetDateTimeTilLocalDateTime(offsetDateTime: OffsetDateTime): LocalDateTime {
        return offsetDateTime.toLocalDateTime()
    }
}
