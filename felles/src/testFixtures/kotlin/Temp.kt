package no.nav.helsearbeidsgiver.felles.test
import io.kotest.matchers.maps.shouldContainAll
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toMap

infix fun Map<Key, JsonElement>.shouldContainAllExcludingTempKey(expected: Map<Key, JsonElement>) {
    this.minus(Key.DATA) shouldContainAll expected.minus(Key.DATA)
    this[Key.DATA]?.toMap().orEmpty() shouldContainAll expected[Key.DATA]?.toMap().orEmpty()
}
