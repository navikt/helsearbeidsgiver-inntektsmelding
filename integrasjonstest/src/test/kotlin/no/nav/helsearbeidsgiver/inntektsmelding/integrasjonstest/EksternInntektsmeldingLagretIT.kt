package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.januar
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EksternInntektsmeldingLagretIT : EndToEndTest() {

    @Test
    fun `lagre ekstern inntektsmelding hvis ikke fra nav_no`() {
        forespoerselRepository.lagreForespoersel(Mock.forespoerselId.toString(), Mock.ORGNR)
        forespoerselRepository.oppdaterSakId(Mock.forespoerselId.toString(), Mock.SAK_ID)
        forespoerselRepository.oppdaterOppgaveId(Mock.forespoerselId.toString(), Mock.OPPGAVE_ID)
        val eksternInntektsmelding = EksternInntektsmelding(
            "AltinnPortal",
            "1.63",
            "AR123456",
            11.januar(2018).atStartOfDay()
        )
        every { spinnKlient.hentEksternInntektsmelding(any()) } returns eksternInntektsmelding

        publish(
            Key.EVENT_NAME to EventName.EKSTERN_INNTEKTSMELDING_REQUESTED.toJson(),
            Key.UUID to Mock.transaksjonId.toJson(),
            Key.DATA to mapOf(
                Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                Key.SPINN_INNTEKTSMELDING_ID to Mock.spinnInntektsmeldingId.toJson()
            ).toJson()
        )

        messages.filter(EventName.EKSTERN_INNTEKTSMELDING_REQUESTED)
            .filter(BehovType.HENT_EKSTERN_INNTEKTSMELDING)
            .firstAsMap()
            .also {
                Key.EVENT_NAME.les(EventName.serializer(), it) shouldBe EventName.EKSTERN_INNTEKTSMELDING_REQUESTED
                Key.BEHOV.les(BehovType.serializer(), it) shouldBe BehovType.HENT_EKSTERN_INNTEKTSMELDING
                Key.UUID.les(UuidSerializer, it) shouldBe Mock.transaksjonId

                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.FORESPOERSEL_ID.les(UuidSerializer, data) shouldBe Mock.forespoerselId
                Key.SPINN_INNTEKTSMELDING_ID.les(UuidSerializer, data) shouldBe Mock.spinnInntektsmeldingId
            }

        messages.filter(EventName.EKSTERN_INNTEKTSMELDING_MOTTATT)
            .filter(BehovType.LAGRE_EKSTERN_INNTEKTSMELDING)
            .firstAsMap()
            .also {
                Key.EVENT_NAME.les(EventName.serializer(), it) shouldBe EventName.EKSTERN_INNTEKTSMELDING_MOTTATT
                Key.BEHOV.les(BehovType.serializer(), it) shouldBe BehovType.LAGRE_EKSTERN_INNTEKTSMELDING
                Key.UUID.les(UuidSerializer, it) shouldBe Mock.transaksjonId
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
                Key.EKSTERN_INNTEKTSMELDING.les(EksternInntektsmelding.serializer(), it) shouldBe eksternInntektsmelding
            }

        messages.filter(EventName.EKSTERN_INNTEKTSMELDING_LAGRET)
            .firstAsMap()
            .also {
                Key.EVENT_NAME.les(EventName.serializer(), it) shouldBe EventName.EKSTERN_INNTEKTSMELDING_LAGRET
                Key.UUID.les(UuidSerializer, it) shouldBe Mock.transaksjonId
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
            }
    }

    private object Mock {
        const val ORGNR = "sur-gubbe"
        const val SAK_ID = "tjukk-gubbe"
        const val OPPGAVE_ID = "kunstig-gubbe"

        val forespoerselId: UUID = UUID.randomUUID()
        val transaksjonId: UUID = UUID.randomUUID()
        val spinnInntektsmeldingId: UUID = UUID.randomUUID()
    }
}
