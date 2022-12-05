@file:UseSerializers(UuidSerializer::class, LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import no.nav.helsearbeidsgiver.felles.serializers.UuidSerializer
import java.time.LocalDate
import java.util.UUID

@Serializable
data class ForespørselSvar(
    val orgnr: String,
    val fnr: String,
    val vedtaksperiodeId: UUID,
    val fom: LocalDate,
    val tom: LocalDate,
    val forespurtData: List<ForespurtData>
)
