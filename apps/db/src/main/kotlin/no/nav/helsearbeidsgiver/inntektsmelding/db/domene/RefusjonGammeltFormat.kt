@file:Suppress("DEPRECATION")
@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.db.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import java.time.LocalDate

@Deprecated("Kun brukt for å lese gamle databaserader.")
@Serializable
data class RefusjonGammeltFormat(
    val utbetalerHeleEllerDeler: Boolean,
    val refusjonPrMnd: Double? = null,
    val refusjonOpphører: LocalDate? = null,
    val refusjonEndringer: List<RefusjonEndringGammeltFormat>? = null,
)

@Deprecated("Kun brukt for å lese gamle databaserader.")
@Serializable
data class RefusjonEndringGammeltFormat(
    val beløp: Double? = null,
    val dato: LocalDate? = null,
)
