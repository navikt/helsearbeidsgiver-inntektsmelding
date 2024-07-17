package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.mockk.every
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.fromJsonToString
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ForespoerselMottattIT : EndToEndTest() {
    @Test
    fun `skal ta imot forespørsel ny inntektsmelding, deretter opprette sak og oppgave`() {
        mockStatic(::randomUuid) {
            every { randomUuid() } returns Mock.transaksjonId

            publish(
                Pri.Key.NOTIS to Pri.NotisType.FORESPØRSEL_MOTTATT.toJson(Pri.NotisType.serializer()),
                Pri.Key.ORGNR to Mock.ORGNR.toJson(),
                Pri.Key.FNR to Mock.FNR.toJson(),
                Pri.Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
            )
        }

        messages
            .filter(EventName.FORESPØRSEL_MOTTATT)
            .filter(BehovType.LAGRE_FORESPOERSEL)
            .firstAsMap()
            .also {
                it[Key.EVENT_NAME]?.fromJson(EventName.serializer()) shouldBe EventName.FORESPØRSEL_MOTTATT
                it[Key.BEHOV]?.fromJson(BehovType.serializer()) shouldBe BehovType.LAGRE_FORESPOERSEL
                it[Key.ORGNRUNDERENHET]?.fromJsonToString() shouldBe Mock.ORGNR
                it[Key.IDENTITETSNUMMER]?.fromJsonToString() shouldBe Mock.FNR
                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
                it[Key.UUID]?.fromJson(UuidSerializer) shouldBe Mock.transaksjonId
            }

        messages
            .filter(EventName.FORESPØRSEL_LAGRET)
            .firstAsMap()
            .also {
                it shouldNotContainKey Key.BEHOV
                it[Key.EVENT_NAME]?.fromJson(EventName.serializer()) shouldBe EventName.FORESPØRSEL_LAGRET
                it[Key.ORGNRUNDERENHET]?.fromJsonToString() shouldBe Mock.ORGNR
                it[Key.IDENTITETSNUMMER]?.fromJsonToString() shouldBe Mock.FNR
                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
            }
    }

    private object Mock {
         var FNR = "fnr-rebekka"
        const val ORGNR = "orgnr-gås"

        val forespoerselId = UUID.randomUUID()
        val transaksjonId: UUID = UUID.randomUUID()
    }
}
