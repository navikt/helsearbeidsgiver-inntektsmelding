package no.nav.hag.simba.utils.rr.fixtures

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.rr.test.Messages
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr

class MessagesTest :
    FunSpec({

        test("finner korrekt melding for event") {
            val expectedEventName = EventName.TRENGER_REQUESTED

            val funnetMelding = Mock.meldinger.filter(expectedEventName).firstAsMap()

            val actualEventName = funnetMelding[Key.EVENT_NAME]?.fromJson(EventName.serializer())

            actualEventName shouldBe expectedEventName
        }

        test("finner ikke manglende melding for event") {
            Mock.meldinger
                .filter(EventName.FORESPOERSEL_MOTTATT)
                .all()
                .shouldBeEmpty()
        }

        context("finner korrekt melding for behov") {
            val expectedBehovType = BehovType.HENT_VIRKSOMHET_NAVN

            val funnetMelding = Mock.meldinger.filter(expectedBehovType).firstAsMap()

            val actualBehovType = funnetMelding[Key.BEHOV]?.fromJson(BehovType.serializer())

            actualBehovType shouldBe expectedBehovType
        }

        test("finner ikke manglende melding for behov") {
            Mock.meldinger
                .filter(BehovType.HENT_LAGRET_IM)
                .all()
                .shouldBeEmpty()
        }

        test("finner korrekt melding for key") {
            val funnetMelding = Mock.meldinger.filter(Key.VIRKSOMHET).firstAsMap()

            funnetMelding.also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.VIRKSOMHET]?.fromJson(String.serializer()) shouldBe Mock.orgnr
            }
        }

        test("finner ikke manglende melding for key") {
            Mock.meldinger
                .filter(Key.ANSETTELSESPERIODER)
                .all()
                .shouldBeEmpty()
        }
    })

private object Mock {
    val orgnr = Orgnr.genererGyldig().verdi

    val meldinger =
        mapOf(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
            Key.BEHOV to BehovType.HENT_VIRKSOMHET_NAVN.toJson(),
            Key.DATA to
                mapOf(
                    Key.VIRKSOMHET to orgnr.toJson(),
                ).toJson(),
        ).toJson()
            .toMessages()

    private fun JsonElement.toMessages(): Messages = Messages(mutableListOf(this))
}
