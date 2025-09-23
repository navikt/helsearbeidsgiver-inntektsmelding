package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import kotlinx.serialization.builtins.serializer
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.lesOrNull
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.test.mock.mockInntektsmeldingV1
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HentSelvbestemtIT : EndToEndTest() {
    @BeforeEach
    fun setup() {
        truncateDatabase()
    }

    @Test
    fun `selvbestemt inntektsmelding hentes`() {
        val kontekstId: UUID = UUID.randomUUID()
        val inntektsmelding =
            mockInntektsmeldingV1().copy(
                type =
                    Inntektsmelding.Type.Selvbestemt(
                        id = UUID.randomUUID(),
                    ),
            )

        selvbestemtImRepo.lagreIm(inntektsmelding)

        publish(
            Key.EVENT_NAME to EventName.SELVBESTEMT_IM_REQUESTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(UuidSerializer),
            Key.DATA to
                mapOf(
                    Key.SELVBESTEMT_ID to inntektsmelding.type.id.toJson(UuidSerializer),
                ).toJson(),
        )

        // Ingen feil
        messages.filterFeil().all().shouldBeEmpty()

        // Behov publiseres
        messages
            .filter(EventName.SELVBESTEMT_IM_REQUESTED)
            .filter(BehovType.HENT_SELVBESTEMT_IM)
            .firstAsMap()
            .let { msg ->
                Key.KONTEKST_ID.lesOrNull(UuidSerializer, msg) shouldBe kontekstId

                val data = msg[Key.DATA].shouldNotBeNull().toMap()
                Key.SELVBESTEMT_ID.lesOrNull(UuidSerializer, data) shouldBe inntektsmelding.type.id
            }

        // Behov besvares
        messages
            .filter(EventName.SELVBESTEMT_IM_REQUESTED)
            .filter(Key.SELVBESTEMT_INNTEKTSMELDING)
            .firstAsMap()
            .let { msg ->
                Key.KONTEKST_ID.lesOrNull(UuidSerializer, msg) shouldBe kontekstId

                val data = msg[Key.DATA].shouldNotBeNull().toMap()
                Key.SELVBESTEMT_ID.lesOrNull(UuidSerializer, data) shouldBe inntektsmelding.type.id
                Key.SELVBESTEMT_INNTEKTSMELDING.lesOrNull(Inntektsmelding.serializer(), data) shouldBe inntektsmelding
            }

        // Funnet inntektsmelding legges i Redis
        val redisResponse =
            redisConnection
                .get(RedisPrefix.HentSelvbestemtIm, kontekstId)
                .shouldNotBeNull()
                .fromJson(ResultJson.serializer())

        redisResponse.success.shouldNotBeNull().fromJson(Inntektsmelding.serializer()) shouldBe inntektsmelding
        redisResponse.failure.shouldBeNull()
    }

    @Test
    fun `selvbestemt inntektsmelding finnes ikke`() {
        val kontekstId: UUID = UUID.randomUUID()
        val inntektsmelding =
            mockInntektsmeldingV1().copy(
                type =
                    Inntektsmelding.Type.Selvbestemt(
                        id = UUID.randomUUID(),
                    ),
            )

        publish(
            Key.EVENT_NAME to EventName.SELVBESTEMT_IM_REQUESTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(UuidSerializer),
            Key.DATA to
                mapOf(
                    Key.SELVBESTEMT_ID to inntektsmelding.type.id.toJson(UuidSerializer),
                ).toJson(),
        )

        // Én feil
        messages.filterFeil().all() shouldHaveSize 1

        // Behov publiseres
        messages
            .filter(EventName.SELVBESTEMT_IM_REQUESTED)
            .filter(BehovType.HENT_SELVBESTEMT_IM)
            .firstAsMap()
            .let { msg ->
                Key.KONTEKST_ID.lesOrNull(UuidSerializer, msg) shouldBe kontekstId

                val data = msg[Key.DATA].shouldNotBeNull().toMap()
                Key.SELVBESTEMT_ID.lesOrNull(UuidSerializer, data) shouldBe inntektsmelding.type.id
            }

        // Behov besvares med feil
        messages
            .filterFeil()
            .firstAsMap()
            .let {
                val fail = Key.FAIL.lesOrNull(Fail.serializer(), it).shouldNotBeNull()

                Key.KONTEKST_ID.lesOrNull(UuidSerializer, fail.utloesendeMelding) shouldBe kontekstId
                Key.BEHOV.lesOrNull(BehovType.serializer(), fail.utloesendeMelding) shouldBe BehovType.HENT_SELVBESTEMT_IM
            }

        // Funnet feilmelding legges i Redis
        val redisResponse =
            redisConnection
                .get(RedisPrefix.HentSelvbestemtIm, kontekstId)
                .shouldNotBeNull()
                .fromJson(ResultJson.serializer())

        redisResponse.success.shouldBeNull()
        redisResponse.failure
            .shouldNotBeNull()
            .fromJson(String.serializer())
            .shouldNotBeEmpty()
    }
}
