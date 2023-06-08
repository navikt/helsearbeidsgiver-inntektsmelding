package no.nav.helsearbeidsgiver.felles.json

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.loeser.Løsning
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson

fun JsonNode.toJsonElement(): JsonElement =
    toString().parseJson()

fun <T : Any> KSerializer<T>.løsning(): KSerializer<Løsning<T>> =
    Løsning.serializer(this)

fun EventName.toJson(): JsonElement =
    toJson(EventName.serializer())
fun BehovType.toJson(): JsonElement =
    toJson(BehovType.serializer())
fun DataFelt.toJson(): JsonElement =
    toJson(DataFelt.serializer())
