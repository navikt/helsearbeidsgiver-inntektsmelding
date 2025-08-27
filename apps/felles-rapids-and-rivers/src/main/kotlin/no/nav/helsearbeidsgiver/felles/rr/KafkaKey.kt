package no.nav.helsearbeidsgiver.felles.rr

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

@Serializable
@JvmInline
value class KafkaKey private constructor(
    internal val key: String,
) {
    companion object {
        operator fun invoke(forespoerselId: UUID): KafkaKey = KafkaKey(forespoerselId.toString())

        operator fun invoke(sykmeldtFnr: Fnr): KafkaKey = KafkaKey(sykmeldtFnr.verdi)
    }

    fun toJson(): JsonElement = toJson(serializer())
}
