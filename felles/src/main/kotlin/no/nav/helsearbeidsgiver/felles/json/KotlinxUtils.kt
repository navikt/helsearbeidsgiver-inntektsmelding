package no.nav.helsearbeidsgiver.felles.json

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.loeser.Løsning
import no.nav.helsearbeidsgiver.utils.json.parseJson

fun JsonNode.toJsonElement(): JsonElement =
    toString().parseJson()

fun <T : Any> KSerializer<T>.løsning(): KSerializer<Løsning<T>> =
    Løsning.serializer(this)
