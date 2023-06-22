package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Løsning
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered

fun JsonElement.fromJsonToString(): String =
    fromJson(String.serializer())

fun <T : Løsning> Map<Key, JsonElement>.lesLoesning(behovType: BehovType, loesningSerializer: KSerializer<T>): T? =
    this[Key.LØSNING]
        ?.fromJsonMapFiltered(BehovType.serializer())
        ?.get(behovType)
        ?.fromJson(loesningSerializer)
