@file:UseSerializers(UuidSerializer::class)

package no.nav.hag.simba.utils.felles.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.util.UUID

@Serializable
data class VedtaksperiodeIdForespoerselIdPar(
    val vedtaksperiodeId: UUID,
    val forespoerselId: UUID,
)
