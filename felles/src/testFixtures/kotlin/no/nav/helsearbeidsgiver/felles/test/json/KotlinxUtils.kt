package no.nav.helsearbeidsgiver.felles.test.json

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap

fun JsonElement.lesBehov(): BehovType? = Key.BEHOV.lesOrNull(BehovType.serializer(), this.toMap())

fun Map<Key, JsonElement>.plusData(dataField: Pair<Key, JsonElement>): Map<Key, JsonElement> {
    val data = this[Key.DATA]?.toMap().orEmpty()
    return this.plus(
        Key.DATA to data.plus(dataField).toJson(),
    )
}

fun Map<Key, JsonElement>.minusData(vararg dataKey: Key): Map<Key, JsonElement> {
    val data = this[Key.DATA]?.toMap().orEmpty()
    return this.plus(
        Key.DATA to data.minus(dataKey.toSet()).toJson(),
    )
}
