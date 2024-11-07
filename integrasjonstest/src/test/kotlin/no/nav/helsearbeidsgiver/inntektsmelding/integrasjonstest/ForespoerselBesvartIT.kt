package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
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
        publish(
            Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.toJson(Pri.NotisType.serializer()),
            Pri.Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
            Pri.Key.SPINN_INNTEKTSMELDING_ID to Mock.spinnInntektsmeldingId.toJson(),
        )
        messages
            .filter(EventName.FORESPOERSEL_BESVART)
            .firstAsMap()
            .also {
                it shouldContainKey Key.UUID

                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
            }

        messages
            .filter(EventName.SAK_OG_OPPGAVE_FERDIGSTILT)
            .firstAsMap()
            .also {
                it shouldContainKey Key.UUID

                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
            }

        messages
            .filter(EventName.EKSTERN_INNTEKTSMELDING_REQUESTED)
            .filter(BehovType.HENT_EKSTERN_INNTEKTSMELDING)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.SPINN_INNTEKTSMELDING_ID.les(UuidSerializer, data) shouldBe Mock.spinnInntektsmeldingId
            }
    }

    private object Mock {
        val forespoerselId: UUID = UUID.randomUUID()
        val spinnInntektsmeldingId: UUID = UUID.randomUUID()
    }
}
