package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.TrengerData
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyKeys
import no.nav.helsearbeidsgiver.felles.test.mock.mockTrengerInntekt
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrengerIT : EndToEndTest() {

    @Test
    fun `Test trengerIM meldingsflyt`() {
        var transactionId: UUID

        publish(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
            Key.CLIENT_ID to Mock.clientId.toJson(UuidSerializer),
            Key.ARBEIDSGIVER_ID to "12345678910".toJson(),
            DataFelt.FORESPOERSEL_ID to Mock.forespoerselId.toJson(UuidSerializer)
        )

        messages.filter(EventName.TRENGER_REQUESTED)
            .filter(BehovType.HENT_TRENGER_IM)
            .first()
            .fromJsonMapOnlyKeys()
            .let {
                // Ble lagret i databasen
                transactionId = it[Key.UUID].shouldNotBeNull().fromJson(UuidSerializer)
            }

        publish(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
            Key.DATA to "".toJson(),
            Key.UUID to transactionId.toJson(),
            DataFelt.FORESPOERSEL_SVAR to mockTrengerInntekt().toJson(TrengerInntekt.serializer())
        )

        waitForMessages(12000)

        messages.filter(EventName.TRENGER_REQUESTED)
            .filter(BehovType.HENT_TRENGER_IM)
            .first()
            .fromJsonMapOnlyKeys()
            .let {
                // Ble lagret i databasen
                it[Key.UUID]?.fromJson(UuidSerializer) shouldBe transactionId
            }

        messages.filter(EventName.TRENGER_REQUESTED)
            .filter(BehovType.VIRKSOMHET)
            .first()
            .fromJsonMapOnlyKeys()
            .let {
                // Ble lagret i databasen
                it[Key.UUID]?.fromJson(UuidSerializer) shouldBe transactionId
            }

        messages.filter(EventName.TRENGER_REQUESTED)
            .filter(BehovType.FULLT_NAVN)
            .first()
            .fromJsonMapOnlyKeys()
            .let {
                // Ble lagret i databasen
                it[Key.UUID]?.fromJson(UuidSerializer) shouldBe transactionId
            }

        messages.filter(EventName.TRENGER_REQUESTED)
            .filter(BehovType.INNTEKT)
            .first()
            .fromJsonMapOnlyKeys()
            .let {
                // Ble lagret i databasen
                it[Key.UUID]?.fromJson(UuidSerializer) shouldBe transactionId
            }

        val trengerResultatJson = redisStore.get(RedisKey.of(Mock.clientId.toString()))
        println("In test $trengerResultatJson")
        val objekt = trengerResultatJson?.fromJson(TrengerData.serializer())
        println(objekt)
    }

    private object Mock {
        val clientId: UUID = UUID.randomUUID()
        val forespoerselId: UUID = UUID.randomUUID()
    }
}
