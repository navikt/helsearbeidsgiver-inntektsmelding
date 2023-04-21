package no.nav.helsearbeidsgiver.felles.test.json

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.fromJsonMap
import no.nav.helsearbeidsgiver.felles.utils.mapKeysNotNull

fun JsonElement.fromJsonMapOnlyKeys(): Map<Key, JsonElement> =
    fromJsonMap(String.serializer())
        .mapKeysNotNull {
            tryOrNull {
                "\"$it\"".fromJson(Key.serializer())
            }
        }

internal fun <T : Any> tryOrNull(block: () -> T): T? =
    runCatching(block).getOrNull()
