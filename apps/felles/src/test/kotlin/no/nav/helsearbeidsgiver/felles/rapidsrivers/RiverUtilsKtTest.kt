package no.nav.helsearbeidsgiver.felles.rapidsrivers

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.clearAllMocks
import io.mockk.spyk
import io.mockk.verifySequence
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonNull
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

class RiverUtilsKtTest :
    FunSpec({

        val testRapid = spyk(TestRapid())

        beforeTest {
            clearAllMocks()
        }

        context("publish") {

            test("vararg pairs (fnr-key)") {
                val key = Fnr.genererGyldig()
                val melding =
                    arrayOf(
                        Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
                        Key.INNTEKTSMELDING to mockInntektsmeldingV1().toJson(Inntektsmelding.serializer()),
                        Key.FNR_LISTE to setOf("111", "333", "555").toJson(String.serializer()),
                    )

                testRapid.publish(key, *melding)

                verifySequence {
                    testRapid.publish(
                        key.toString(),
                        withArg<String> {
                            it.parseJson().toMap() shouldContainExactly melding.toMap()
                        },
                    )
                }
            }

            test("vararg pairs (UUID-key)") {
                val key = UUID.randomUUID()
                val melding =
                    arrayOf(
                        Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
                        Key.INNTEKTSMELDING to mockInntektsmeldingV1().toJson(Inntektsmelding.serializer()),
                        Key.FNR_LISTE to setOf("555", "333", "111").toJson(String.serializer()),
                    )

                testRapid.publish(key, *melding)

                verifySequence {
                    testRapid.publish(
                        key.toString(),
                        withArg<String> {
                            it.parseJson().toMap() shouldContainExactly melding.toMap()
                        },
                    )
                }
            }

            test("map") {
                val key = UUID.randomUUID()
                val melding =
                    mapOf(
                        Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
                        Key.INNTEKTSMELDING to mockInntektsmeldingV1().toJson(Inntektsmelding.serializer()),
                        Key.ORGNR_UNDERENHETER to setOf("222", "444", "666").toJson(String.serializer()),
                    )

                testRapid.publish(key.toString(), melding)

                verifySequence {
                    testRapid.publish(
                        key.toString(),
                        withArg<String> {
                            it.parseJson().toMap() shouldContainExactly melding
                        },
                    )
                }
            }

            test("filtrerer ut JsonNull") {
                val key = UUID.randomUUID()
                val selvbestemtId = UUID.randomUUID()

                testRapid.publish(
                    key.toString(),
                    mapOf(
                        Key.SELVBESTEMT_ID to selvbestemtId.toJson(),
                        Key.FORESPOERSEL_SVAR to JsonNull,
                    ),
                )

                verifySequence {
                    testRapid.publish(
                        key.toString(),
                        withArg<String> {
                            it.parseJson().toMap() shouldContainExactly mapOf(Key.SELVBESTEMT_ID to selvbestemtId.toJson())
                        },
                    )
                }
            }
        }
    })
