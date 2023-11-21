package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.shouldNotBe
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AktiveOrgnrServiceIT : EndToEndTest() {

    @Test
    fun `Test hente aktive organisasjoner`() {
        publish(
            Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
            Key.CLIENT_ID to Mock.clientId.toJson(),
            DataFelt.FNR to Mock.FNR.toJson(),
            DataFelt.ARBEIDSGIVER_FNR to Mock.FNR_AG.toJson()
        )

        Thread.sleep(10000)

        redisStore.get(RedisKey.of(Mock.clientId.toString())) shouldNotBe null

        messages.filter(EventName.AKTIVE_ORGNR_REQUESTED)
    }

    private object Mock {
        const val ORGNR = "stolt-krakk"
        const val FNR = "kongelig-albatross"
        const val FNR_AG = "uutgrunnelig-koffert"

        val clientId = randomUuid()
    }
}
