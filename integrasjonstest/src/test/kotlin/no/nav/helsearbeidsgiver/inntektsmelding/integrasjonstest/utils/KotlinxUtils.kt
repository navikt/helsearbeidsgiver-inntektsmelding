package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.utils.json.fromJson

fun JsonElement.fromJsonToString(): String = fromJson(String.serializer())
