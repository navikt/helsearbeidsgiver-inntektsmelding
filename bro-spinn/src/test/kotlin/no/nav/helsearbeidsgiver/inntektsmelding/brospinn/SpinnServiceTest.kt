package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.clearAllMocks
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.felles.test.json.plusData
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.message
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.time.LocalDateTime
import java.util.UUID

class SpinnServiceTest :
    FunSpec({
        val testRapid = TestRapid()

        ServiceRiverStateless(
            SpinnService(testRapid),
        ).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        test("publiser mottatt-event for inntektsmelding fra LPS/Altinn") {
            testRapid.sendJson(Mock.steg0())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.EKSTERN_INNTEKTSMELDING_REQUESTED.toJson(),
                    Key.BEHOV to BehovType.HENT_EKSTERN_INNTEKTSMELDING.toJson(),
                    Key.UUID to Mock.transaksjonId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                            Key.SPINN_INNTEKTSMELDING_ID to Mock.spinnInntektsmeldingId.toJson(),
                        ).toJson(),
                )

            testRapid.sendJson(Mock.steg1())

            testRapid.inspektør.size shouldBeExactly 2

            testRapid.message(1).toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.EKSTERN_INNTEKTSMELDING_MOTTATT.toJson(),
                    Key.UUID to Mock.transaksjonId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                            Key.EKSTERN_INNTEKTSMELDING to Mock.eksternIm.toJson(EksternInntektsmelding.serializer()),
                            Key.EKSTERN_INNTEKTSMELDING_V2 to Mock.eksternIm.toJson(EksternInntektsmelding.serializer()),
                        ).toJson(),
                )
        }

        context("publiserer ikke mottatt-event for ...") {
            withData(
                mapOf(
                    "forespurt inntektsmelding fra nav.no" to "NAV_NO",
                    "selvbestemt inntektsmelding fra nav.no" to "NAV_NO_SELVBESTEMT",
                ),
            ) { avsenderSystemNavn ->
                val imFraNavNo =
                    Mock.eksternIm.copy(
                        avsenderSystemNavn = avsenderSystemNavn,
                    )

                testRapid.sendJson(Mock.steg0())

                testRapid.inspektør.size shouldBeExactly 1

                testRapid.sendJson(
                    Mock
                        .steg1()
                        .plusData(Key.EKSTERN_INNTEKTSMELDING to imFraNavNo.toJson(EksternInntektsmelding.serializer())),
                )

                testRapid.inspektør.size shouldBeExactly 1
            }
        }
    })

private object Mock {
    val transaksjonId: UUID = UUID.randomUUID()
    val forespoerselId: UUID = UUID.randomUUID()
    val spinnInntektsmeldingId: UUID = UUID.randomUUID()
    val eksternIm =
        EksternInntektsmelding(
            avsenderSystemNavn = "LittPengeSvindel",
            avsenderSystemVersjon = "den andre versjonen",
            arkivreferanse = "archivo apostolico",
            tidspunkt = LocalDateTime.now(),
        )

    fun steg0(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.EKSTERN_INNTEKTSMELDING_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.SPINN_INNTEKTSMELDING_ID to spinnInntektsmeldingId.toJson(),
                ).toJson(),
        )

    fun steg1(): Map<Key, JsonElement> =
        steg0().plusData(
            Key.EKSTERN_INNTEKTSMELDING to eksternIm.toJson(EksternInntektsmelding.serializer()),
        )
}
