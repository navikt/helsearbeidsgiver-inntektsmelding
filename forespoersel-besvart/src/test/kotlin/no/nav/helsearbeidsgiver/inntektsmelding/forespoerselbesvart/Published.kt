@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.util.UUID

@Serializable
data class Published(
    @SerialName("@event_name")
    val eventName: EventName,
    @SerialName("@behov")
    val behov: BehovType,
    val forespoerselId: UUID,
    @SerialName("uuid")
    val transaksjonId: UUID,
) {
    companion object {
        fun mock(): Published =
            Published(
                eventName = EventName.FORESPOERSEL_BESVART,
                behov = BehovType.NOTIFIKASJON_HENT_ID,
                forespoerselId = UUID.randomUUID(),
                transaksjonId = UUID.randomUUID(),
            )
    }
}
