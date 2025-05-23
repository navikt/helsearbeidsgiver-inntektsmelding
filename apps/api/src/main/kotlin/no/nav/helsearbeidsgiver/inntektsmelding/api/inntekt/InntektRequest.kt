@file:UseSerializers(LocalDateSerializer::class, UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonNames
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.time.LocalDate
import java.util.UUID

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class InntektRequest(
    val forespoerselId: UUID,
    @JsonNames("inntektsdato")
    val skjaeringstidspunkt: LocalDate,
)
