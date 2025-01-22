package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.test.mock.mockEksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingGammeltFormat
import no.nav.helsearbeidsgiver.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock.mockForespoerselSvarSuksess
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.desember
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KvitteringIT : EndToEndTest() {
    @BeforeEach
    fun setup() {
        truncateDatabase()
    }

    @Test
    fun `skal hente data til kvittering`() {
        val kontekstId = UUID.randomUUID()
        val skjema = mockSkjemaInntektsmelding()

        mockForespoerselSvarFraHelsebro(
            forespoerselId = skjema.forespoerselId,
            forespoerselSvar =
                mockForespoerselSvarSuksess().copy(
                    forespoerselId = skjema.forespoerselId,
                ),
        )

        val innsendingId = imRepository.lagreInntektsmeldingSkjema(skjema, 3.desember.atStartOfDay())
        imRepository.oppdaterMedBeriketDokument(skjema.forespoerselId, innsendingId, mockInntektsmeldingGammeltFormat())

        publish(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to skjema.forespoerselId.toJson(),
                ).toJson(),
        )

        messages
            .filter(EventName.KVITTERING_REQUESTED)
            .filter(Key.SKJEMA_INNTEKTSMELDING)
            .filter(Key.LAGRET_INNTEKTSMELDING)
            .filter(Key.EKSTERN_INNTEKTSMELDING)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()

                data[Key.SKJEMA_INNTEKTSMELDING] shouldNotBe Mock.tomResultJson
                data[Key.LAGRET_INNTEKTSMELDING] shouldNotBe Mock.tomResultJson

                data[Key.EKSTERN_INNTEKTSMELDING] shouldBe Mock.tomResultJson
            }

        redisConnection.get(RedisPrefix.Kvittering, kontekstId).shouldNotBeNull()
    }

    @Test
    fun `skal hente data til kvittering hvis fra eksternt system`() {
        val kontekstId = UUID.randomUUID()
        val forespoerselId = UUID.randomUUID()

        mockForespoerselSvarFraHelsebro(
            forespoerselId = forespoerselId,
            forespoerselSvar =
                mockForespoerselSvarSuksess().copy(
                    forespoerselId = forespoerselId,
                ),
        )

        imRepository.lagreEksternInntektsmelding(forespoerselId, mockEksternInntektsmelding())

        publish(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                ).toJson(),
        )

        messages
            .filter(EventName.KVITTERING_REQUESTED)
            .filter(Key.SKJEMA_INNTEKTSMELDING)
            .filter(Key.LAGRET_INNTEKTSMELDING)
            .filter(Key.EKSTERN_INNTEKTSMELDING)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()

                data[Key.SKJEMA_INNTEKTSMELDING] shouldBe Mock.tomResultJson
                data[Key.LAGRET_INNTEKTSMELDING] shouldBe Mock.tomResultJson

                val eIm = data[Key.EKSTERN_INNTEKTSMELDING]
                eIm.shouldNotBeNull()
                eIm shouldNotBe Mock.tomResultJson
            }

        redisConnection.get(RedisPrefix.Kvittering, kontekstId).shouldNotBeNull()
    }

    @Test
    fun `skal gi feilmelding når forespørsel ikke finnes`() {
        val forespoerselId = UUID.randomUUID()
        val kontekstId = UUID.randomUUID()

        mockForespoerselSvarFraHelsebro(
            forespoerselId = forespoerselId,
            forespoerselSvar =
                mockForespoerselSvarSuksess().copy(
                    forespoerselId = forespoerselId,
                ),
        )

        publish(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                ).toJson(),
        )

        messages
            .filter(EventName.KVITTERING_REQUESTED)
            .filter(Key.SKJEMA_INNTEKTSMELDING)
            .filter(Key.LAGRET_INNTEKTSMELDING)
            .filter(Key.EKSTERN_INNTEKTSMELDING)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()

                // Skal ikke finne inntektsmeldingdokument
                data[Key.SKJEMA_INNTEKTSMELDING] shouldBe Mock.tomResultJson
                data[Key.LAGRET_INNTEKTSMELDING] shouldBe Mock.tomResultJson
                data[Key.EKSTERN_INNTEKTSMELDING] shouldBe Mock.tomResultJson
            }
    }

    private object Mock {
        val tomResultJson = ResultJson(success = null).toJson()
    }
}
