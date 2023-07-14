package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyDatafelter
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyKeys
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.fromJsonToString
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ForespoerselMottattIT : EndToEndTest() {

    @Test
    fun `skal ta imot forespørsel ny inntektsmelding, deretter opprette sak og oppgave`() {
        publish(
            Pri.Key.NOTIS to Pri.NotisType.FORESPØRSEL_MOTTATT.toJson(Pri.NotisType.serializer()),
            Pri.Key.ORGNR to Mock.ORGNR.toJson(),
            Pri.Key.FNR to Mock.FNR.toJson(),
            Pri.Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson()
        )

        waitForMessages(8000)

        messages.filter(EventName.FORESPØRSEL_MOTTATT)
            .filter(BehovType.LAGRE_FORESPOERSEL, loesningPaakrevd = false)
            .first()
            .also { msg ->
                msg.fromJsonMapOnlyKeys().also {
                    it[Key.EVENT_NAME]?.fromJson(EventName.serializer()) shouldBe EventName.FORESPØRSEL_MOTTATT
                    it[Key.BEHOV]?.fromJson(BehovType.serializer()) shouldBe BehovType.LAGRE_FORESPOERSEL
                    it[Key.IDENTITETSNUMMER]?.fromJsonToString() shouldBe Mock.FNR
                    it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
                }

                msg.fromJsonMapOnlyDatafelter().also {
                    it[DataFelt.ORGNRUNDERENHET]?.fromJsonToString() shouldBe Mock.ORGNR
                }
            }

        messages.filter(EventName.FORESPØRSEL_LAGRET)
            .first()
            .also { msg ->
                msg.fromJsonMapOnlyKeys().also {
                    it shouldNotContainKey Key.BEHOV
                    it[Key.EVENT_NAME]?.fromJson(EventName.serializer()) shouldBe EventName.FORESPØRSEL_LAGRET
                    it[Key.IDENTITETSNUMMER]?.fromJsonToString() shouldBe Mock.FNR
                    it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
                }

                msg.fromJsonMapOnlyDatafelter().also {
                    it[DataFelt.ORGNRUNDERENHET]?.fromJsonToString() shouldBe Mock.ORGNR
                }
            }
    }

    private object Mock {
        const val FNR = "fnr-rebekka"
        const val ORGNR = "orgnr-gås"

        val forespoerselId = UUID.randomUUID()
    }
}
