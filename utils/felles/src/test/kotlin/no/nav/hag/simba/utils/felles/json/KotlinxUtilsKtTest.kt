package no.nav.hag.simba.utils.felles.json

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.Row2
import io.kotest.data.row
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.IKey
import no.nav.hag.simba.utils.felles.Key
import no.nav.helsearbeidsgiver.utils.json.toJson

class KotlinxUtilsKtTest :
    FunSpec({

        context("toMap") {
            test("inneholder alle Keys") {
                val expectedMap =
                    mapOf(
                        Key.EVENT_NAME to "test_event",
                        Key.BEHOV to "test_behov",
                        Key.KONTEKST_ID to "test_kontekstId",
                    )

                val json =
                    JsonObject(
                        expectedMap
                            .mapKeys { it.key.toString() }
                            .mapValues { it.value.toJson() },
                    )

                val jsonMap = json.toMap()

                jsonMap shouldBe expectedMap.mapValues { it.value.toJson() }
            }

            test("inneholder bare Keys") {
                val expectedMap =
                    mapOf(
                        Key.EVENT_NAME to "test_event",
                        Key.BEHOV to "test_behov",
                    )

                val json =
                    JsonObject(
                        expectedMap
                            .mapKeys { it.key.toString() }
                            .plus(
                                "ikke en key" to "skal ikke være med",
                            ).mapValues { it.value.toJson() },
                    )

                val jsonMap = json.toMap()

                jsonMap shouldBe expectedMap.mapValues { it.value.toJson() }
            }
        }

        context("les") {
            withData(
                mapOf<String, Row2<IKey, String>>(
                    "Key leses" to row(Key.EVENT_NAME, "testevent"),
                    "TestKey leses" to row(TestKey.EVENT_RSVP, "husk å drikke vann"),
                ),
            ) { (key, expectedValue) ->
                val jsonMap = mapOf(key to expectedValue.toJson())

                val actualValue = key.les(String.serializer(), jsonMap)

                actualValue shouldBe expectedValue
            }

            test("Key og TestKey.Key leses fra samme map") {

                val jsonMap =
                    mapOf(
                        Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
                        TestKey.EVENT_RSVP to "Kanskje".toJson(String.serializer()),
                    )

                Key.EVENT_NAME.les(EventName.serializer(), jsonMap) shouldBe EventName.TRENGER_REQUESTED
                TestKey.EVENT_RSVP.les(String.serializer(), jsonMap) shouldBe "Kanskje"
            }

            withData(
                mapOf(
                    "gir MeldingException dersom nøkkel ikke finnes" to emptyMap(),
                    "gir MeldingException dersom nøkkel finnes, men verdi er null-json" to mapOf(Key.EVENT_NAME to JsonNull),
                ),
            ) { jsonMap ->
                val e =
                    shouldThrowExactly<MeldingException> {
                        Key.EVENT_NAME.les(EventName.serializer(), jsonMap)
                    }

                e.message shouldBe "Felt '${Key.EVENT_NAME}' mangler i JSON-map."
                e.stackTrace.shouldBeEmpty()
                e.fillInStackTrace().shouldBeNull()
            }
        }
    })

private enum class TestKey : IKey {
    EVENT_RSVP,
}
