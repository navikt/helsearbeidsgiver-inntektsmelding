package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.LagretInntektsmelding
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.test.mock.mockEksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingGammeltFormat
import no.nav.helsearbeidsgiver.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock.mockForespoerselSvarSuksess
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
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
        val inntektsmelding = mockInntektsmeldingGammeltFormat()
        val mottatt = 3.desember.atStartOfDay()

        mockForespoerselSvarFraHelsebro(
            forespoerselId = skjema.forespoerselId,
            forespoerselSvar =
                mockForespoerselSvarSuksess().copy(
                    forespoerselId = skjema.forespoerselId,
                ),
        )

        val innsendingId = imRepository.lagreInntektsmeldingSkjema(skjema, mottatt)
        imRepository.oppdaterMedBeriketDokument(skjema.forespoerselId, innsendingId, inntektsmelding)

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
            .filter(Key.LAGRET_INNTEKTSMELDING)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()

                val success = data[Key.LAGRET_INNTEKTSMELDING].shouldNotBeNull().fromJson(ResultJson.serializer()).success
                success.shouldNotBeNull()
                success.fromJson(LagretInntektsmelding.serializer()) shouldBe
                    LagretInntektsmelding.Skjema(
                        avsenderNavn = inntektsmelding.innsenderNavn,
                        skjema = skjema,
                        mottatt = mottatt,
                    )
            }

        redisConnection.get(RedisPrefix.Kvittering, kontekstId).shouldNotBeNull()
    }

    @Test
    fun `skal hente data til kvittering hvis fra eksternt system`() {
        val kontekstId = UUID.randomUUID()
        val forespoerselId = UUID.randomUUID()
        val eksternIm = mockEksternInntektsmelding()

        mockForespoerselSvarFraHelsebro(
            forespoerselId = forespoerselId,
            forespoerselSvar =
                mockForespoerselSvarSuksess().copy(
                    forespoerselId = forespoerselId,
                ),
        )

        imRepository.lagreEksternInntektsmelding(forespoerselId, eksternIm)

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
            .filter(Key.LAGRET_INNTEKTSMELDING)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()

                val success = data[Key.LAGRET_INNTEKTSMELDING].shouldNotBeNull().fromJson(ResultJson.serializer()).success
                success.shouldNotBeNull()
                success.fromJson(LagretInntektsmelding.serializer()) shouldBe LagretInntektsmelding.Ekstern(eksternIm)
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
            .filter(Key.LAGRET_INNTEKTSMELDING)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()

                data[Key.LAGRET_INNTEKTSMELDING] shouldBe ResultJson(success = null).toJson()
            }
    }
}
