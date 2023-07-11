package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyKeys
import no.nav.helsearbeidsgiver.felles.test.mock.GYLDIG_INNSENDING_REQUEST
import no.nav.helsearbeidsgiver.felles.test.mock.TestData
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.Jackson
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InnsendingServiceIT : EndToEndTest() {

    @Test
    fun `Test at innsending er mottatt`() {
        val forespoerselId = UUID.randomUUID()
        val clientId = UUID.randomUUID()

        forespoerselRepository.lagreForespoersel(forespoerselId.toString(), TestData.validOrgNr)

        publishMessage(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.CLIENT_ID to clientId.toJson(),
            DataFelt.INNTEKTSMELDING to GYLDIG_INNSENDING_REQUEST.let(Jackson::toJson),
            DataFelt.ORGNRUNDERENHET to TestData.validOrgNr.toJson(),
            Key.IDENTITETSNUMMER to TestData.validIdentitetsnummer.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson()
        )

        Thread.sleep(10000)

        messages.all().filter(clientId).size shouldBe 10

        val innsendingStr = redisStore.get(clientId.toString()).shouldNotBeNull()
        innsendingStr.length shouldBeGreaterThan 2
    }
}

private fun List<JsonElement>.filter(clientId: UUID): List<JsonElement> {
    var transaksjonId: UUID? = null
    return filter {
        val msg = it.fromJsonMapOnlyKeys()

        val msgClientId = msg[Key.CLIENT_ID]?.fromJson(UuidSerializer)
        if (msgClientId == clientId) {
            true
        } else {
            val eventName = msg[Key.EVENT_NAME]?.fromJson(EventName.serializer()).shouldNotBeNull()

            if (transaksjonId == null && (eventName == EventName.INSENDING_STARTED && msg.contains(Key.BEHOV))) {
                transaksjonId = msg[Key.UUID]?.fromJson(UuidSerializer)
            }

            val uuid = listOfNotNull(
                msg[Key.UUID],
                msg[Key.TRANSACTION_ORIGIN]
            )
                .firstOrNull()
                .shouldNotBeNull()
                .fromJson(UuidSerializer)

            val innsendingStartetEllerImMottatt = eventName == EventName.INSENDING_STARTED ||
                (eventName == EventName.INNTEKTSMELDING_MOTTATT && !msg.contains(Key.BEHOV))

            uuid == transaksjonId &&
                innsendingStartetEllerImMottatt &&
                !msg.contains(Key.LØSNING)
        }
    }
}
