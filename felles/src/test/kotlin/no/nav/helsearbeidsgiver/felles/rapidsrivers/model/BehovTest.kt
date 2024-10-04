package no.nav.helsearbeidsgiver.felles.rapidsrivers.model

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.utils.json.parseJson
import java.util.UUID

class BehovTest :
    FunSpec({

        test("Lag Fail med samme transaksjonId") {

            val utloesendeMelding =
                JsonMessage.newMessage(
                    mapOf(
                        Key.EVENT_NAME.str to Mock.event,
                        Key.BEHOV.str to Mock.behovType,
                        Key.UUID.str to Mock.transaksjonId.toString(),
                        "snipp" to "snapp",
                    ),
                )

            val behov =
                Behov(
                    event = Mock.event,
                    behov = Mock.behovType,
                    forespoerselId = Mock.forespoerselId.toString(),
                    jsonMessage = utloesendeMelding,
                )

            val fail = behov.createFail(Mock.FEILMELDING)

            val expected =
                Fail(
                    feilmelding = Mock.FEILMELDING,
                    event = Mock.event,
                    transaksjonId = Mock.transaksjonId,
                    forespoerselId = Mock.forespoerselId,
                    utloesendeMelding = utloesendeMelding.toJson().parseJson(),
                )

            fail shouldBe expected
        }

        test("Lag Fail med splitter ny transaksjonId") {
            val utloesendeMelding =
                JsonMessage.newMessage(
                    mapOf(
                        Key.EVENT_NAME.str to Mock.event,
                        Key.BEHOV.str to Mock.behovType,
                        "hepp" to "hei",
                    ),
                )

            val behov =
                Behov(
                    event = Mock.event,
                    behov = Mock.behovType,
                    forespoerselId = Mock.forespoerselId.toString(),
                    jsonMessage = utloesendeMelding,
                )

            val fail = behov.createFail(Mock.FEILMELDING)

            val expected =
                Fail(
                    feilmelding = Mock.FEILMELDING,
                    event = Mock.event,
                    transaksjonId = UUID.randomUUID(),
                    forespoerselId = Mock.forespoerselId,
                    utloesendeMelding = utloesendeMelding.toJson().parseJson(),
                )

            fail.shouldBeEqualToIgnoringFields(expected, Fail::transaksjonId)
        }
    })

private object Mock {
    val event = EventName.FORESPØRSEL_LAGRET
    val behovType = BehovType.HENT_PERSONER
    val transaksjonId: UUID = UUID.randomUUID()
    val forespoerselId: UUID = UUID.randomUUID()
    const val FEILMELDING = "feilmelding"
}
