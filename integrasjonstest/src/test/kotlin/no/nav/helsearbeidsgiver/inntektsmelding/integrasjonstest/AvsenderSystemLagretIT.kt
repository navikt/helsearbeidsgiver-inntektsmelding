package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.mockk.every
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.felles.AvsenderSystemData
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyDatafelter
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyKeys
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AvsenderSystemLagretIT : EndToEndTest() {

    @Test
    fun `ved notis om besvart forespørsel så lagres avsenderSystem hvis ikke fra nav_no`() {
        forespoerselRepository.lagreForespoersel(Mock.forespoerselId.toString(), Mock.ORGNR)
        forespoerselRepository.oppdaterSakId(Mock.forespoerselId.toString(), Mock.SAK_ID)
        forespoerselRepository.oppdaterOppgaveId(Mock.forespoerselId.toString(), Mock.OPPGAVE_ID)
        val avsenderSystemData = AvsenderSystemData(
            "AltinnPortal",
            "1.63",
            "AR123456"
        )
        // imRepository.lagreAvsenderSystemData(Mock.forespoerselId.toString(), avsenderSystemData)

        mockStatic(::randomUuid) {
            every { randomUuid() } returns Mock.transaksjonId

            publish(
                Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.toJson(Pri.NotisType.serializer()),
                Pri.Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                Pri.Key.SPINN_INNTEKTSMELDING_ID to Mock.spinnInntektsmeldinigId.toJson()
            )

            waitForMessages(20000)
        }

        messages.filter(EventName.FORESPOERSEL_BESVART)
            .filter(BehovType.NOTIFIKASJON_HENT_ID)
            .first()
            .fromJsonMapOnlyKeys()
            .also {
                Key.EVENT_NAME.les(EventName.serializer(), it) shouldBe EventName.FORESPOERSEL_BESVART
                Key.BEHOV.les(BehovType.serializer(), it) shouldBe BehovType.NOTIFIKASJON_HENT_ID
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
                Key.TRANSACTION_ORIGIN.les(UuidSerializer, it) shouldBe Mock.transaksjonId
            }

        messages.filter(EventName.FORESPOERSEL_BESVART)
            .all()
            .first { msg ->
                msg.fromJsonMapOnlyDatafelter().let {
                    it.containsKey(DataFelt.SAK_ID) &&
                        it.containsKey(DataFelt.OPPGAVE_ID)
                }
            }
            .also { msg ->
                msg.fromJsonMapOnlyKeys().also {
                    it shouldNotContainKey Key.BEHOV
                    Key.EVENT_NAME.les(EventName.serializer(), it) shouldBe EventName.FORESPOERSEL_BESVART
                    Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
                    Key.TRANSACTION_ORIGIN.les(UuidSerializer, it) shouldBe Mock.transaksjonId
                }

                msg.fromJsonMapOnlyDatafelter().also {
                    DataFelt.SAK_ID.les(String.serializer(), it) shouldBe Mock.SAK_ID
                    DataFelt.OPPGAVE_ID.les(String.serializer(), it) shouldBe Mock.OPPGAVE_ID
                }
            }

        messages.filter(EventName.SAK_FERDIGSTILT)
            .all()
            .first { msg ->
                msg.fromJsonMapOnlyDatafelter().containsKey(DataFelt.SAK_ID)
            }
            .also { msg ->
                msg.fromJsonMapOnlyKeys().also {
                    Key.EVENT_NAME.les(EventName.serializer(), it) shouldBe EventName.SAK_FERDIGSTILT
                    Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
                    Key.TRANSACTION_ORIGIN.les(UuidSerializer, it) shouldBe Mock.transaksjonId
                }

                msg.fromJsonMapOnlyDatafelter().also {
                    DataFelt.SAK_ID.les(String.serializer(), it) shouldBe Mock.SAK_ID
                }
            }

        messages.filter(EventName.OPPGAVE_FERDIGSTILT)
            .all()
            .first { msg ->
                msg.fromJsonMapOnlyDatafelter().containsKey(DataFelt.OPPGAVE_ID)
            }
            .also { msg ->
                msg.fromJsonMapOnlyKeys().also {
                    Key.EVENT_NAME.les(EventName.serializer(), it) shouldBe EventName.OPPGAVE_FERDIGSTILT
                    Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
                    Key.TRANSACTION_ORIGIN.les(UuidSerializer, it) shouldBe Mock.transaksjonId
                }

                msg.fromJsonMapOnlyDatafelter().also {
                    DataFelt.OPPGAVE_ID.les(String.serializer(), it) shouldBe Mock.OPPGAVE_ID
                }
            }
    }

    private object Mock {
        const val ORGNR = "sur-moskus"
        const val SAK_ID = "tjukk-kalender"
        const val OPPGAVE_ID = "kunstig-demon"

        val forespoerselId = randomUuid()
        val transaksjonId = randomUuid()
        val spinnInntektsmeldinigId = randomUuid()
    }
}
