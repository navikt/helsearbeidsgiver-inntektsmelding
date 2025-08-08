package no.nav.helsearbeidsgiver.felles.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import kotlinx.serialization.json.JsonNull
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.Key
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
                        Key.FAIL to fail.toJson(Fail.serializer()),
                    )
            }
        }
    })

private fun mockFail(): Fail =
    Fail(
        feilmelding = "Det skukke verra mulig",
        kontekstId = UUID.randomUUID(),
        utloesendeMelding =
            mapOf(
                Key.INNTEKTSMELDING to mockInntektsmeldingV1().toJson(Inntektsmelding.serializer()),
                Key.ER_DUPLIKAT_IM to JsonNull,
            ),
    )
