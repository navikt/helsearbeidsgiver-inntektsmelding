@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.felles

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.Serializable
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.serializers.JsonAsStringSerializer
import java.util.UUID

sealed class Løsning {
    abstract val value: Any?
    abstract val error: Feilmelding?
}

data class Data<T>(
    val t: T? = null
)

data class Feil(
    @JsonIgnore
    val eventName: EventName?,
    val behov: BehovType?,
    val feilmelding: String,
    val data: HashMap<DataFelt, Any>?,
    val uuid: String?,
    val forespørselId: String?
)

fun Feil.toJsonMessage(): JsonMessage =
    JsonMessage.newMessage(
        mapOf(
            Key.EVENT_NAME.str to (this.eventName ?: ""),
            Key.FAIL.str to this,
            Key.UUID.str to (this.uuid ?: "")
        )
    )
fun JsonMessage.toFeilMessage(): Feil {
    return customObjectMapper().treeToValue(this[Key.FAIL.str], Feil::class.java).copy(eventName = EventName.valueOf(this[Key.EVENT_NAME.str].asText()))
}

fun JsonMessage.createFail(feilmelding: String, data: HashMap<DataFelt, Any>? = null, behoveType: BehovType? = null): Feil {
    val behovNode: JsonNode? = this.valueNullable(Key.BEHOV)
    // behovtype trenger å vare definert eksplisit da behov elemente er en List
    val behov: BehovType? = behoveType ?: if (behovNode != null) BehovType.valueOf(behovNode.asText()) else null
    val forespørselId = this.valueNullableOrUndefined(Key.FORESPOERSEL_ID)?.asText()
    val eventName = this.valueNullableOrUndefined(Key.EVENT_NAME)?.asText()
    return Feil(eventName?.let { EventName.valueOf(eventName) }, behov, feilmelding, data, this.valueNullable(Key.UUID)?.asText(), forespørselId)
}

@Serializable
data class Feilmelding(
    val melding: String,
    val status: Int? = null
)

@Serializable
data class NavnLøsning(
    override val value: PersonDato? = null,
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class VirksomhetLøsning(
    override val value: String? = null,
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class InntektLøsning(
    override val value: Inntekt? = null,
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class ArbeidsforholdLøsning(
    override val value: List<Arbeidsforhold> = emptyList(),
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class JournalpostLøsning(
    override val value: String? = null,
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class NotifikasjonLøsning(
    override val value: String? = null,
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class HentTrengerImLøsning(
    override val value: TrengerInntekt? = null,
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class PreutfyltLøsning(
    override val value: PersonLink? = null,
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class PersisterImLøsning(
    @Serializable(with = JsonAsStringSerializer::class)
    override val value: String? = null,
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class HentPersistertLøsning(
    override val value: String? = null,
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class HentImOrgnrLøsning(
    override val value: String? = null,
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class TilgangskontrollLøsning(
    override val value: Tilgang? = null,
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class LagreJournalpostLøsning(
    override val value: String? = null,
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class PersisterSakIdLøsning(
    override val value: String? = null,
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class PersisterOppgaveIdLøsning(
    override val value: String? = null,
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class SakFerdigLøsning(
    override val value: String? = null,
    override val error: Feilmelding? = null
) : Løsning()

@Serializable
data class OppgaveFerdigLøsning(
    override val value: String? = null,
    override val error: Feilmelding? = null
) : Løsning()
