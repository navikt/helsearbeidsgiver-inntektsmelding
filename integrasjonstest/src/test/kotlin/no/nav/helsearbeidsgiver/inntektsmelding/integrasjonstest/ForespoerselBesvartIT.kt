package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.felles.BehovType
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ForespoerselBesvartIT : EndToEndTest() {
    @BeforeEach
    fun setup() {
        truncateDatabase()
    }

    @Test
    fun `ved notis om besvart forespørsel så ferdigstilles sak og oppgave`() {
        forespoerselRepository.lagreForespoersel(Mock.forespoerselId.toString(), Mock.ORGNR)
        forespoerselRepository.oppdaterSakId(Mock.forespoerselId.toString(), Mock.SAK_ID)
        forespoerselRepository.oppdaterOppgaveId(Mock.forespoerselId.toString(), Mock.OPPGAVE_ID)

        mockStatic(::randomUuid) {
            every { randomUuid() } returns Mock.transaksjonId

            publish(
                Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.toJson(Pri.NotisType.serializer()),
                Pri.Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                Pri.Key.SPINN_INNTEKTSMELDING_ID to Mock.spinnInntektsmeldingId.toJson(),
            )
        }

        bekreftForventedeMeldinger()

        messages
            .filter(EventName.EKSTERN_INNTEKTSMELDING_REQUESTED)
            .filter(BehovType.HENT_EKSTERN_INNTEKTSMELDING)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.SPINN_INNTEKTSMELDING_ID.les(UuidSerializer, data) shouldBe Mock.spinnInntektsmeldingId
            }
    }

    @Test
    fun `ved mottatt inntektsmelding så ferdigstilles sak og oppgave`() {
        forespoerselRepository.lagreForespoersel(Mock.forespoerselId.toString(), Mock.ORGNR)
        forespoerselRepository.oppdaterSakId(Mock.forespoerselId.toString(), Mock.SAK_ID)
        forespoerselRepository.oppdaterOppgaveId(Mock.forespoerselId.toString(), Mock.OPPGAVE_ID)

        publish(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
            Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
            Key.UUID to Mock.transaksjonId.toJson(),
        )

        bekreftForventedeMeldinger()
    }

    private fun bekreftForventedeMeldinger() {
        messages
            .filter(EventName.FORESPOERSEL_BESVART)
            .filter(BehovType.NOTIFIKASJON_HENT_ID)
            .firstAsMap()
            .also {
                Key.EVENT_NAME.les(EventName.serializer(), it) shouldBe EventName.FORESPOERSEL_BESVART
                Key.BEHOV.les(BehovType.serializer(), it) shouldBe BehovType.NOTIFIKASJON_HENT_ID
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
                Key.UUID.les(UuidSerializer, it) shouldBe Mock.transaksjonId
            }

        messages
            .filter(EventName.FORESPOERSEL_BESVART)
            .filter(Key.SAK_ID, utenDataKey = true)
            .firstAsMap()
            .also {
                it shouldNotContainKey Key.BEHOV
                Key.EVENT_NAME.les(EventName.serializer(), it) shouldBe EventName.FORESPOERSEL_BESVART
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
                Key.UUID.les(UuidSerializer, it) shouldBe Mock.transaksjonId
                Key.SAK_ID.les(String.serializer(), it) shouldBe Mock.SAK_ID
            }

        messages
            .filter(EventName.FORESPOERSEL_BESVART)
            .filter(Key.OPPGAVE_ID, utenDataKey = true)
            .firstAsMap()
            .also {
                it shouldNotContainKey Key.BEHOV
                Key.EVENT_NAME.les(EventName.serializer(), it) shouldBe EventName.FORESPOERSEL_BESVART
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
                Key.UUID.les(UuidSerializer, it) shouldBe Mock.transaksjonId
                Key.OPPGAVE_ID.les(String.serializer(), it) shouldBe Mock.OPPGAVE_ID
            }

        messages
            .filter(EventName.SAK_FERDIGSTILT)
            .filter(Key.SAK_ID, utenDataKey = true)
            .firstAsMap()
            .also {
                Key.EVENT_NAME.les(EventName.serializer(), it) shouldBe EventName.SAK_FERDIGSTILT
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
                Key.UUID.les(UuidSerializer, it) shouldBe Mock.transaksjonId
                Key.SAK_ID.les(String.serializer(), it) shouldBe Mock.SAK_ID
            }

        messages
            .filter(EventName.OPPGAVE_FERDIGSTILT)
            .filter(Key.OPPGAVE_ID, utenDataKey = true)
            .firstAsMap()
            .also {
                Key.EVENT_NAME.les(EventName.serializer(), it) shouldBe EventName.OPPGAVE_FERDIGSTILT
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
                Key.UUID.les(UuidSerializer, it) shouldBe Mock.transaksjonId
                Key.OPPGAVE_ID.les(String.serializer(), it) shouldBe Mock.OPPGAVE_ID
            }
    }

    private object Mock {
        const val ORGNR = "sur-moskus"
        const val SAK_ID = "tjukk-kalender"
        const val OPPGAVE_ID = "kunstig-demon"

        val forespoerselId = randomUuid()
        val transaksjonId = randomUuid()
        val spinnInntektsmeldingId = randomUuid()
    }
}
