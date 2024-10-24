package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.mock.mockEksternInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EksternInntektsmeldingLagretIT : EndToEndTest() {
    @Test
    fun `lagre ekstern inntektsmelding hvis ikke fra nav_no`() {
        every { spinnKlient.hentEksternInntektsmelding(any()) } returns mockEksternInntektsmelding()

        publish(
            Key.EVENT_NAME to EventName.EKSTERN_INNTEKTSMELDING_REQUESTED.toJson(),
            Key.UUID to Mock.transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                    Key.SPINN_INNTEKTSMELDING_ID to Mock.spinnInntektsmeldingId.toJson(),
                ).toJson(),
        )

        messages
            .filter(EventName.EKSTERN_INNTEKTSMELDING_REQUESTED)
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

        messages
            .filter(EventName.EKSTERN_INNTEKTSMELDING_MOTTATT)
            .filter(BehovType.LAGRE_EKSTERN_INNTEKTSMELDING)
            .firstAsMap()
            .also {
                Key.EVENT_NAME.les(EventName.serializer(), it) shouldBe EventName.EKSTERN_INNTEKTSMELDING_MOTTATT
                Key.BEHOV.les(BehovType.serializer(), it) shouldBe BehovType.LAGRE_EKSTERN_INNTEKTSMELDING
                Key.UUID.les(UuidSerializer, it) shouldBe Mock.transaksjonId

                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.FORESPOERSEL_ID.les(UuidSerializer, data) shouldBe Mock.forespoerselId
                Key.EKSTERN_INNTEKTSMELDING.les(EksternInntektsmelding.serializer(), data) shouldBe mockEksternInntektsmelding()
            }

        messages
            .filter(EventName.EKSTERN_INNTEKTSMELDING_LAGRET)
            .firstAsMap()
            .also {
                Key.EVENT_NAME.les(EventName.serializer(), it) shouldBe EventName.EKSTERN_INNTEKTSMELDING_LAGRET
                Key.UUID.les(UuidSerializer, it) shouldBe Mock.transaksjonId
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
            }
    }

    private object Mock {
        val forespoerselId: UUID = UUID.randomUUID()
        val transaksjonId: UUID = UUID.randomUUID()
        val spinnInntektsmeldingId: UUID = UUID.randomUUID()
    }
}
