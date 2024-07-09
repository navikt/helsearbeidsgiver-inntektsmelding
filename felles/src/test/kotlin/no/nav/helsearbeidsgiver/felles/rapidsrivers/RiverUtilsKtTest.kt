package no.nav.helsearbeidsgiver.felles.rapidsrivers

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.clearAllMocks
import io.mockk.spyk
import io.mockk.verifySequence
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonNull
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class RiverUtilsKtTest :
    FunSpec({

        val testRapid = spyk(TestRapid())

        beforeTest {
            clearAllMocks()
        }

        context("publish") {

            test("vararg pairs") {
                val melding =
                    arrayOf(
                        Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
                        Key.INNTEKTSMELDING to mockInntektsmeldingV1().toJson(Inntektsmelding.serializer()),
                        Key.FNR_LISTE to listOf("111", "333", "555").toJson(String.serializer()),
                    )

                testRapid.publish(*melding)

                verifySequence {
                    testRapid.publish(
                        withArg<String> {
                            it.parseJson().toMap() shouldContainExactly melding.toMap()
                        },
                    )
                }
            }

            test("map") {
                val melding =
                    mapOf(
                        Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
                        Key.INNTEKTSMELDING to mockInntektsmeldingV1().toJson(Inntektsmelding.serializer()),
                        Key.ORGNRUNDERENHETER to listOf("222", "444", "666").toJson(String.serializer()),
                    )

                testRapid.publish(melding)

                verifySequence {
                    testRapid.publish(
                        withArg<String> {
                            it.parseJson().toMap() shouldContainExactly melding
                        },
                    )
                }
            }

            test("filtrerer ut JsonNull") {
                val selvbestemtId = UUID.randomUUID()

                testRapid.publish(
                    mapOf(
                        Key.SELVBESTEMT_ID to selvbestemtId.toJson(),
                        Key.FORESPOERSEL_SVAR to JsonNull,
                    ),
                )

                verifySequence {
                    testRapid.publish(
                        withArg<String> {
                            it.parseJson().toMap() shouldContainExactly mapOf(Key.SELVBESTEMT_ID to selvbestemtId.toJson())
                        },
                    )
                }
            }
        }
    })
