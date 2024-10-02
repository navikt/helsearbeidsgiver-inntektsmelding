package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.HentForespoerselResultat
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock.mockForespoerselSvarSuksess
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HentForespoerselIT : EndToEndTest() {
    @Test
    fun `forespørsel hentes`() {
        val transaksjonId: UUID = UUID.randomUUID()
        val forespoerselId: UUID = UUID.randomUUID()

        mockForespoerselSvarFraHelsebro(
            forespoerselId = forespoerselId,
            forespoerselSvar = mockForespoerselSvarSuksess(),
        )

        publish(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(UuidSerializer),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(UuidSerializer),
                    Key.ARBEIDSGIVER_FNR to Fnr.genererGyldig().toJson(),
                ).toJson(),
        )

        messages
            .filter(EventName.TRENGER_REQUESTED)
            .filter(BehovType.HENT_TRENGER_IM)
            .firstAsMap()
            .let {
                // Ble lagret i databasen
                it[Key.UUID]?.fromJson(UuidSerializer) shouldBe transaksjonId
            }

        messages
            .filter(EventName.TRENGER_REQUESTED)
            .filter(BehovType.HENT_VIRKSOMHET_NAVN)
            .firstAsMap()
            .let {
                // Ble lagret i databasen
                it[Key.UUID]?.fromJson(UuidSerializer) shouldBe transaksjonId
            }

        messages
            .filter(EventName.TRENGER_REQUESTED)
            .filter(BehovType.HENT_PERSONER)
            .firstAsMap()
            .let {
                // Ble lagret i databasen
                it[Key.UUID]?.fromJson(UuidSerializer) shouldBe transaksjonId
            }

        messages
            .filter(EventName.TRENGER_REQUESTED)
            .filter(BehovType.HENT_INNTEKT)
            .firstAsMap()
            .let {
                // Ble lagret i databasen
                it[Key.UUID]?.fromJson(UuidSerializer) shouldBe transaksjonId
            }

        val resultJson =
            redisConnection
                .get(RedisPrefix.HentForespoersel, transaksjonId)
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
}
