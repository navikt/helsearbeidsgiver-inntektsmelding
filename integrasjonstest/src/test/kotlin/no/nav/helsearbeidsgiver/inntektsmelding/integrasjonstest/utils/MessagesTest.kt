package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson

class MessagesTest : FunSpec({

    test("finner korrekt melding for event") {
        val expectedEventName = EventName.TRENGER_REQUESTED

        val funnetMelding = Mock.meldingerMedBehov.filter(expectedEventName).firstAsMap()

        val actualEventName = funnetMelding[Key.EVENT_NAME]?.fromJson(EventName.serializer())

        actualEventName shouldBe expectedEventName
    }

    test("finner ikke manglende melding for event") {
        Mock.meldingerMedBehov.filter(EventName.FORESPØRSEL_MOTTATT)
            .all()
            .shouldBeEmpty()
    }

    context("finner korrekt melding for behov") {
        withData(
            BehovType.FULLT_NAVN,
            BehovType.VIRKSOMHET
        ) { expectedBehovType ->
            val funnetMelding = Mock.meldingerMedBehov.filter(expectedBehovType).firstAsMap()

            funnetMelding.also {
                val behovJson = it[Key.BEHOV].shouldNotBeNull()

                behovJson.fromJson(BehovType.serializer().list()) shouldContain expectedBehovType
            }
        }
    }

    test("finner ikke manglende melding for behov") {
        Mock.meldingerMedBehov.filter(BehovType.HENT_IM_ORGNR)
            .all()
            .shouldBeEmpty()
    }

    test("finner korrekt melding for datafelt") {
        val funnetMelding = Mock.meldingerMedDatafelt.filter(DataFelt.VIRKSOMHET).firstAsMap()

        funnetMelding.also {
            it shouldContainKey Key.DATA
            it[DataFelt.VIRKSOMHET]?.fromJson(String.serializer()) shouldBe Mock.ORGNR
        }
    }

    test("finner ikke manglende melding for datafelt") {
        Mock.meldingerMedDatafelt.filter(DataFelt.ARBEIDSFORHOLD)
            .all()
            .shouldBeEmpty()
    }
})

private object Mock {
    const val ORGNR = "orgnr-pai"

    val meldingerMedBehov = basisfelt().toJson().toMessages()
    val meldingerMedDatafelt = basisfelt().plus(datafelt()).toJson().toMessages()

    private fun basisfelt(): Map<String, JsonElement> =
        mapOf(
            Key.EVENT_NAME.str to EventName.TRENGER_REQUESTED.toJson(EventName.serializer()),
            Key.BEHOV.str to listOf(
                BehovType.FULLT_NAVN,
                BehovType.VIRKSOMHET,
                BehovType.ARBEIDSFORHOLD
            ).toJson(BehovType.serializer()),
            Key.DATA.str to "".toJson()
        )

    private fun datafelt(): Map<String, JsonElement> =
        mapOf(
            DataFelt.VIRKSOMHET.str to ORGNR.toJson()
        )

    private fun JsonElement.toMessages(): Messages =
        Messages(mutableListOf(this))
}
