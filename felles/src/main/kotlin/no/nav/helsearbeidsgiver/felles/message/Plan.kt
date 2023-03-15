package no.nav.helsearbeidsgiver.felles.message

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import no.nav.helsearbeidsgiver.felles.BehovType

typealias Delplan = Set<BehovType>

@Serializable(PlanSerializer::class)
data class Plan internal constructor(
    internal val verdi: List<Delplan>
) {
    constructor(vararg f: Delplan) : this(f.toList())

    fun finnFørsteUfullførteDelplan(løsteBehov: Set<BehovType>): Delplan? =
        verdi.firstOrNull {
            !it.isContainedIn(løsteBehov)
        }
}

fun Delplan.løsesAv(løsteBehov: Set<BehovType>): Boolean =
    isContainedIn(løsteBehov)

private fun <T : Any> Iterable<T>.isContainedIn(other: Iterable<T>): Boolean =
    subtract(other).isEmpty()

@OptIn(ExperimentalSerializationApi::class)
object PlanSerializer : KSerializer<Plan> {
    private val delegate = ListSerializer(SetSerializer(BehovType.serializer()))

    override val descriptor = SerialDescriptor("helsearbeidsgiver.felles.Plan", delegate.descriptor)
    override fun serialize(encoder: Encoder, value: Plan) = encoder.encodeSerializableValue(delegate, value.verdi)
    override fun deserialize(decoder: Decoder): Plan = decoder.decodeSerializableValue(delegate).let(::Plan)
}
