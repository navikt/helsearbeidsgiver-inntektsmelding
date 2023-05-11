package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import java.math.BigDecimal
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

fun LocalDate.toNorsk(): String {
    return this.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
}

fun LocalDateTime.toNorsk(): String {
    return this.format(DateTimeFormatter.ofPattern("dd.MM.yyyy ' kl. ' HH.mm.ss"))
}

fun OffsetDateTime.toNorsk(): String {
    return this.format(DateTimeFormatter.ofPattern("dd.MM.yyyy ' kl. ' HH.mm.ss"))
}

fun BigDecimal.toNorsk(): String {
    val format = DecimalFormat("#,###.##")
    return format.format(this)
}

fun Boolean.toNorsk(): String {
    return if (this) { "Ja" } else { "Nei" }
}
