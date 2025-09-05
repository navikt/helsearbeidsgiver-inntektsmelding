package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.lesOrNull
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.kontrakt.domene.inntektsmelding.EksternInntektsmelding
import no.nav.hag.simba.utils.kontrakt.domene.inntektsmelding.test.mockEksternInntektsmelding
import no.nav.hag.simba.utils.kontrakt.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ForespoerselBesvartIT : EndToEndTest() {
    @BeforeEach
    fun setup() {
        truncateDatabase()
    }

    @Test
    fun `ved notis om besvart forespørsel så ferdigstilles sak og oppgave`() {
        val forespoerselId: UUID = UUID.randomUUID()
        val spinnInntektsmeldingId: UUID = UUID.randomUUID()
        val eksternIm = mockEksternInntektsmelding()

        every { spinnKlient.hentEksternInntektsmelding(any()) } returns eksternIm

        publish(
            Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.toJson(Pri.NotisType.serializer()),
            Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Pri.Key.SPINN_INNTEKTSMELDING_ID to spinnInntektsmeldingId.toJson(),
        )

        messages
            .filter(EventName.FORESPOERSEL_BESVART)
            .firstAsMap()
            .also {
                it shouldContainKey Key.KONTEKST_ID

                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.FORESPOERSEL_ID.les(UuidSerializer, data) shouldBe forespoerselId
            }

        messages
            .filter(EventName.SAK_OG_OPPGAVE_FERDIGSTILT)
            .firstAsMap()
            .also {
                it shouldContainKey Key.KONTEKST_ID

                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe forespoerselId
            }

        messages
            .filter(EventName.EKSTERN_INNTEKTSMELDING_MOTTATT)
            .firstAsMap()
            .also {
                it shouldContainKey Key.KONTEKST_ID

                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, data) shouldBe forespoerselId
                Key.EKSTERN_INNTEKTSMELDING.lesOrNull(EksternInntektsmelding.serializer(), data) shouldBe eksternIm
            }

        messages
            .filter(EventName.EKSTERN_INNTEKTSMELDING_LAGRET)
            .firstAsMap()
            .also {
                it shouldContainKey Key.KONTEKST_ID

                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe forespoerselId
            }
    }
}
