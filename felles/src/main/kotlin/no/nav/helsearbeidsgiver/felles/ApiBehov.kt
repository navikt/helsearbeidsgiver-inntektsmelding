package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import no.nav.helsearbeidsgiver.felles.message.Plan

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("@behov")
data class ApiBehov<T : ApiBehov.Input>(
    val plan: Plan,
    val input: T
) {
    @Serializable
    sealed class Input {
        @Serializable
        @SerialName("Arbeidsgivere")
        data class Arbeidsgivere(
            val identitetsnummer: Identitetsnummer
        ) : Input()

        @Serializable
        @SerialName("PreutfyltSkjema")
        data class PreutfyltSkjema(
            val forespoerselId: String
        ) : Input()
    }
}
