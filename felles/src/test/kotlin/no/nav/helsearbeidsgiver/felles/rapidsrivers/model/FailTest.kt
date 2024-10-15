package no.nav.helsearbeidsgiver.felles.rapidsrivers.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import kotlinx.serialization.json.JsonNull
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail.Companion.serializer
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class FailTest :
    FunSpec({

        context(Fail::tilMelding.name) {

            test("har med forventede felter") {
                val fail = mockFail()

                fail.tilMelding() shouldContainExactly
                    mapOf(
                        Key.FAIL to fail.toJson(serializer()),
                        Key.EVENT_NAME to fail.event.toJson(),
                        Key.UUID to fail.transaksjonId.toJson(),
                        Key.FORESPOERSEL_ID to fail.forespoerselId?.toJson(),
                    )
            }

            test("inkluderer _ikke_ ${Key.FORESPOERSEL_ID} dersom verdi er 'null'") {
                val failUtenForespoerselId = mockFail().copy(forespoerselId = null)

                failUtenForespoerselId.tilMelding() shouldContainExactly
                    mapOf(
                        Key.FAIL to failUtenForespoerselId.toJson(serializer()),
                        Key.EVENT_NAME to failUtenForespoerselId.event.toJson(),
                        Key.UUID to failUtenForespoerselId.transaksjonId.toJson(),
                    )
            }
        }
    })

private fun mockFail(): Fail =
    Fail(
        feilmelding = "Det skukke verra mulig",
        event = EventName.TILGANG_FORESPOERSEL_REQUESTED,
        transaksjonId = UUID.randomUUID(),
        forespoerselId = UUID.randomUUID(),
        utloesendeMelding =
            mapOf(
                Key.INNTEKTSMELDING to mockInntektsmeldingV1().toJson(Inntektsmelding.serializer()),
                Key.ER_DUPLIKAT_IM to JsonNull,
            ).toJson(),
    )
