package no.nav.helsearbeidsgiver.felles.loeser

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class LøsningSerializer<T : Any>(tSerializer: KSerializer<T>) : KSerializer<Løsning<T>> {
    @Serializable
    @SerialName("Løsning")
    @OptIn(ExperimentalSerializationApi::class)
    data class Surrogate<out T : Any>(
        val løsningType: Type,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val resultat: T? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val feilmelding: String? = null
    ) {
        enum class Type { SUCCESS, FAILURE }
    }

    private val surrogateSerializer = Surrogate.serializer(tSerializer)

    override val descriptor: SerialDescriptor = surrogateSerializer.descriptor

    override fun serialize(encoder: Encoder, value: Løsning<T>) {
        val surrogate = when (value) {
            is Løsning.Success ->
                Surrogate(
                    løsningType = Surrogate.Type.SUCCESS,
                    resultat = value.resultat
                )
            is Løsning.Failure ->
                Surrogate(
                    løsningType = Surrogate.Type.FAILURE,
                    feilmelding = value.feilmelding
                )
        }
        surrogateSerializer.serialize(encoder, surrogate)
    }

    override fun deserialize(decoder: Decoder): Løsning<T> {
        val surrogate = surrogateSerializer.deserialize(decoder)
        return when (surrogate.løsningType) {
            Surrogate.Type.SUCCESS ->
                surrogate.resultat
                    ?.toLøsningSuccess()
                    ?: throw DeserializationException(Surrogate<*>::resultat.name)
            Surrogate.Type.FAILURE ->
                surrogate.feilmelding
                    ?.toLøsningFailure()
                    ?: throw DeserializationException(Surrogate<*>::feilmelding.name)
        }
    }

    class DeserializationException(feltnavn: String) : SerializationException(
        "Felt '$feltnavn' mangler eller har verdi 'null'."
    )
}
