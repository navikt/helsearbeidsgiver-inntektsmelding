package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.shouldBe
import io.mockk.every
import no.nav.helsearbeidsgiver.felles.AvsenderSystemData
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
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
    fun `lagre avsenderSystem hvis ikke fra nav_no`() {
        forespoerselRepository.lagreForespoersel(Mock.forespoerselId.toString(), Mock.ORGNR)
        forespoerselRepository.oppdaterSakId(Mock.forespoerselId.toString(), Mock.SAK_ID)
        forespoerselRepository.oppdaterOppgaveId(Mock.forespoerselId.toString(), Mock.OPPGAVE_ID)
        val avsenderSystemData = AvsenderSystemData(
            "AltinnPortal",
            "1.63",
            "AR123456"
        )
        every { spinnKlient.hentAvsenderSystemData(any()) } returns avsenderSystemData
        // imRepository.lagreAvsenderSystemData(Mock.forespoerselId.toString(), avsenderSystemData)

        mockStatic(::randomUuid) {
            every { randomUuid() } returns Mock.transaksjonId

            publish(
                Key.EVENT_NAME to EventName.AVSENDER_REQUESTED.toJson(),
                Pri.Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                Pri.Key.SPINN_INNTEKTSMELDING_ID to Mock.spinnInntektsmeldinigId.toJson()
            )
            Thread.sleep(10000)
        }

        messages.filter(EventName.AVSENDER_REQUESTED)
            .filter(BehovType.HENT_AVSENDER_SYSTEM)
            .first().toMap()
            .also {
                Key.EVENT_NAME.les(EventName.serializer(), it) shouldBe EventName.AVSENDER_REQUESTED
                Key.BEHOV.les(BehovType.serializer(), it) shouldBe BehovType.HENT_AVSENDER_SYSTEM
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
                DataFelt.SPINN_INNTEKTSMELDING_ID.les(UuidSerializer, it) shouldBe Mock.spinnInntektsmeldinigId
            }

        messages.filter(EventName.EKSTERN_INNTEKTSMELDING_MOTTATT)
            .filter(BehovType.LAGRE_AVSENDER_SYSTEM)
            .first().toMap()
            .also {
                Key.EVENT_NAME.les(EventName.serializer(), it) shouldBe EventName.EKSTERN_INNTEKTSMELDING_MOTTATT
                Key.BEHOV.les(BehovType.serializer(), it) shouldBe BehovType.LAGRE_AVSENDER_SYSTEM
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
                DataFelt.AVSENDER_SYSTEM_DATA.les(AvsenderSystemData.serializer(), it) shouldBe avsenderSystemData
            }

        messages.filter(EventName.AVSENDER_SYSTEM_LAGRET)
            .first().toMap()
            .also {
                Key.EVENT_NAME.les(EventName.serializer(), it) shouldBe EventName.AVSENDER_SYSTEM_LAGRET
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
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
