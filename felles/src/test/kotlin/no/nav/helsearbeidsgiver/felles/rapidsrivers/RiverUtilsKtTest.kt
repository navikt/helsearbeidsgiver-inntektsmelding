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
                        Key.FNR_LISTE to setOf("111", "333", "555").toJson(String.serializer()),
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
                        Key.ORGNR_UNDERENHETER to setOf("222", "444", "666").toJson(String.serializer()),
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

            test("duplikert verdi i Data MapOf i SPINN_INNTEKTSMELDING_ID nøkkel") {

                val verdi = ("unik ORGNRUNDERENHET verdi " + UUID.randomUUID().toString()).toJson()

                val melding =
                    mapOf(
                        Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
                        Key.DATA to
                            mapOf(
                                Key.SPINN_INNTEKTSMELDING_ID to verdi,
                            ).toJson(),
                    )

                testRapid.publish(melding)

                val meldingMedDuplikert =
                    melding.plus(
                        Key.DATA to
                            mapOf(
                                Key.SPINN_INNTEKTSMELDING_ID to verdi,
                                Key.SPINN_INNTEKTSMELDING_ID_V2 to verdi,
                            ).toJson(),
                    )

                verifySequence {
                    testRapid.publish(
                        withArg<String> {
                            it.parseJson().toMap() shouldContainExactly meldingMedDuplikert
                        },
                    )
                }
            }

            test("duplikert verdi i nøkkel og Data MapOf nøkkel") {

                val verdi = ("unik ORGNRUNDERENHET verdi " + UUID.randomUUID().toString()).toJson()
                val id = Key.FORESPOERSEL_ID to UUID.randomUUID().toJson()

                val melding =
                    mapOf(
                        id,
                        Key.SPINN_INNTEKTSMELDING_ID to verdi,
                        Key.DATA to
                            mapOf(
                                Key.SPINN_INNTEKTSMELDING_ID to verdi,
                            ).toJson(),
                    )

                testRapid.publish(melding)

                val meldingMedDuplikert =
                    mapOf(
                        id,
                        Key.SPINN_INNTEKTSMELDING_ID to verdi,
                        Key.SPINN_INNTEKTSMELDING_ID_V2 to verdi,
                        Key.DATA to
                            mapOf(
                                Key.SPINN_INNTEKTSMELDING_ID to verdi,
                                Key.SPINN_INNTEKTSMELDING_ID_V2 to verdi,
                            ).toJson(),
                    )

                verifySequence {
                    testRapid.publish(
                        withArg<String> {

                            it.parseJson().toMap() shouldContainExactly meldingMedDuplikert
                        },
                    )
                }
            }
        }
    })
