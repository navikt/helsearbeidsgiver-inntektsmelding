package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.HentForespoerselResultat
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock.mockForespoerselSvarSuksess
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrengerIT : EndToEndTest() {

    @Test
    fun `Test trengerIM meldingsflyt`() {
        val transaksjonId: UUID = UUID.randomUUID()

        mockForespoerselSvarFraHelsebro(
            eventName = EventName.TRENGER_REQUESTED,
            transaksjonId = transaksjonId,
            forespoerselId = Mock.forespoerselId,
            forespoerselSvar = mockForespoerselSvarSuksess()
        )

        mockStatic(::randomUuid) {
            every { randomUuid() } returns transaksjonId

            publish(
                Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
                Key.CLIENT_ID to Mock.clientId.toJson(UuidSerializer),
                Key.ARBEIDSGIVER_ID to Fnr.genererGyldig().toJson(Fnr.serializer()),
                Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(UuidSerializer)
            )
        }

        messages.filter(EventName.TRENGER_REQUESTED)
            .filter(BehovType.HENT_TRENGER_IM)
            .firstAsMap()
            .let {
                // Ble lagret i databasen
                it[Key.UUID]?.fromJson(UuidSerializer) shouldBe transaksjonId
            }

        messages.filter(EventName.TRENGER_REQUESTED)
            .filter(BehovType.VIRKSOMHET)
            .firstAsMap()
            .let {
                // Ble lagret i databasen
                it[Key.UUID]?.fromJson(UuidSerializer) shouldBe transaksjonId
            }

        messages.filter(EventName.TRENGER_REQUESTED)
            .filter(BehovType.FULLT_NAVN)
            .firstAsMap()
            .let {
                // Ble lagret i databasen
                it[Key.UUID]?.fromJson(UuidSerializer) shouldBe transaksjonId
            }

        messages.filter(EventName.TRENGER_REQUESTED)
            .filter(BehovType.INNTEKT)
            .firstAsMap()
            .let {
                // Ble lagret i databasen
                it[Key.UUID]?.fromJson(UuidSerializer) shouldBe transaksjonId
            }

        val resultJson = redisStore.get(RedisKey.of(Mock.clientId))
            ?.fromJson(ResultJson.serializer())
            .shouldNotBeNull()

        resultJson.failure.shouldBeNull()

        val hentForespoerselResultat = resultJson.success.shouldNotBeNull().fromJson(HentForespoerselResultat.serializer())

        hentForespoerselResultat.shouldNotBeNull().apply {
            sykmeldtNavn.shouldNotBeNull()
            avsenderNavn.shouldNotBeNull()
            orgNavn.shouldNotBeNull()
            inntekt.shouldNotBeNull()
            forespoersel.shouldNotBeNull()
            feil.shouldBeEmpty()
        }
    }

    private object Mock {
        val clientId: UUID = UUID.randomUUID()
        val forespoerselId: UUID = UUID.randomUUID()
    }
}
