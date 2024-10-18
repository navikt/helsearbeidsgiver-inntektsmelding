package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.test.mock.randomDigitString
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
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
        forespoerselRepository.lagreForespoersel(Mock.forespoerselId.toString(), Mock.orgnr.verdi)
        forespoerselRepository.oppdaterSakId(Mock.forespoerselId.toString(), Mock.sakId)
        forespoerselRepository.oppdaterOppgaveId(Mock.forespoerselId.toString(), Mock.oppgaveId)

        publish(
            Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.toJson(Pri.NotisType.serializer()),
            Pri.Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
            Pri.Key.SPINN_INNTEKTSMELDING_ID to Mock.spinnInntektsmeldingId.toJson(),
        )

        bekreftForventedeMeldinger(forventetTransaksjonId = null)

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
        val transaksjonId: UUID = UUID.randomUUID()

        forespoerselRepository.lagreForespoersel(Mock.forespoerselId.toString(), Mock.orgnr.verdi)
        forespoerselRepository.oppdaterSakId(Mock.forespoerselId.toString(), Mock.sakId)
        forespoerselRepository.oppdaterOppgaveId(Mock.forespoerselId.toString(), Mock.oppgaveId)

        publish(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
        )

        bekreftForventedeMeldinger(forventetTransaksjonId = transaksjonId)
    }

    private fun bekreftForventedeMeldinger(forventetTransaksjonId: UUID?) {
        messages
            .filter(EventName.FORESPOERSEL_BESVART)
            .firstAsMap()
            .also {
                if (forventetTransaksjonId == null) {
                    it shouldContainKey Key.UUID
                } else {
                    Key.UUID.les(UuidSerializer, it) shouldBe forventetTransaksjonId
                }

                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
            }

        messages
            .filter(EventName.SAK_OG_OPPGAVE_FERDIGSTILT)
            .firstAsMap()
            .also {
                if (forventetTransaksjonId == null) {
                    it shouldContainKey Key.UUID
                } else {
                    Key.UUID.les(UuidSerializer, it) shouldBe forventetTransaksjonId
                }

                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
            }
    }

    private object Mock {
        val forespoerselId: UUID = UUID.randomUUID()
        val spinnInntektsmeldingId: UUID = UUID.randomUUID()
        val orgnr = Orgnr.genererGyldig()
        val sakId = randomDigitString(21)
        val oppgaveId = randomDigitString(14)
    }
}
