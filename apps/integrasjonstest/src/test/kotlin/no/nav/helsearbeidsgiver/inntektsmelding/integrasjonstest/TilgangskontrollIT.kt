package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.server.request.ApplicationRequest
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Tilgang
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.kafka.Producer
import no.nav.helsearbeidsgiver.felles.test.kafka.mockRecordMetadata
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.ManglerAltinnRettigheterException
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.Tilgangskontroll
import no.nav.helsearbeidsgiver.inntektsmelding.api.auth.lesFnrFraAuthToken
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock.mockForespoerselSvarSuksess
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TilgangskontrollIT : EndToEndTest() {
    val mockKafkaProducer = mockk<KafkaProducer<String, JsonElement>>()
    val mockRequest = mockk<ApplicationRequest>(relaxed = true)

    val tilgangskontroll by lazy {
        Tilgangskontroll(
            producer = Producer("test-topic", mockKafkaProducer),
            cache = LocalCache(0.minutes, 1),
            redisConnection = redisConnection,
        )
    }

    @BeforeEach
    fun beforeEach() {
        clearAllMocks()

        val recordSlot = slot<ProducerRecord<String, JsonElement>>()
        every { mockKafkaProducer.send(capture(recordSlot)).get() } answers {
            val message =
                recordSlot.captured
                    .value()
                    .toMap()
                    .toList()
                    .toTypedArray()

            publish(*message)

            mockRecordMetadata()
        }

        coEvery {
            altinnClient.harTilgangTilOrganisasjon(fnr = Mock.innloggetFnr.verdi, orgnr = Mock.orgnrMedTilgang.verdi)
        } returns true

        coEvery {
            altinnClient.harTilgangTilOrganisasjon(fnr = Mock.innloggetFnr.verdi, orgnr = Mock.orgnrUtenTilgang.verdi)
        } returns false
    }

    @Test
    fun `forespoersel - skal få tilgang`() {
        mockForespoerselSvarFraHelsebro(
            forespoerselId = Mock.forespoerselId,
            forespoerselSvar =
                mockForespoerselSvarSuksess().copy(
                    forespoerselId = Mock.forespoerselId,
                    orgnr = Mock.orgnrMedTilgang,
                ),
        )

        mockStatic(ApplicationRequest::lesFnrFraAuthToken) {
            every { mockRequest.lesFnrFraAuthToken() } returns Mock.innloggetFnr

            tilgangskontroll.validerTilgangTilForespoersel(mockRequest, Mock.forespoerselId)
        }

        messages
            .filter(EventName.TILGANG_FORESPOERSEL_REQUESTED)
            .filter(BehovType.HENT_TRENGER_IM)
            .firstAsMap()
            .also {
                Key.KONTEKST_ID.lesOrNull(UuidSerializer, it).shouldNotBeNull()

                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, data) shouldBe Mock.forespoerselId
            }

        val result =
            messages
                .filter(EventName.TILGANG_FORESPOERSEL_REQUESTED)
                .filter(Key.TILGANG)
                .firstAsMap()

        val tilgang =
            result[Key.DATA]
                .shouldNotBeNull()
                .toMap()[Key.TILGANG]
                .shouldNotBeNull()
                .fromJson(Tilgang.serializer())

        tilgang shouldBe Tilgang.HAR_TILGANG
    }

    @Test
    fun `forespoersel - skal bli nektet tilgang`() {
        mockForespoerselSvarFraHelsebro(
            forespoerselId = Mock.forespoerselId,
            forespoerselSvar =
                mockForespoerselSvarSuksess().copy(
                    forespoerselId = Mock.forespoerselId,
                    orgnr = Mock.orgnrUtenTilgang,
                ),
        )

        mockStatic(ApplicationRequest::lesFnrFraAuthToken) {
            every { mockRequest.lesFnrFraAuthToken() } returns Mock.innloggetFnr

            shouldThrowExactly<ManglerAltinnRettigheterException> {
                tilgangskontroll.validerTilgangTilForespoersel(mockRequest, Mock.forespoerselId)
            }
        }

        val result =
            messages
                .filter(EventName.TILGANG_FORESPOERSEL_REQUESTED)
                .filter(Key.TILGANG)
                .firstAsMap()

        val tilgang =
            result[Key.DATA]
                .shouldNotBeNull()
                .toMap()[Key.TILGANG]
                .shouldNotBeNull()
                .fromJson(Tilgang.serializer())

        tilgang shouldBe Tilgang.IKKE_TILGANG
    }

    @Test
    fun `organisasjon - skal få tilgang`() {
        mockStatic(ApplicationRequest::lesFnrFraAuthToken) {
            every { mockRequest.lesFnrFraAuthToken() } returns Mock.innloggetFnr

            tilgangskontroll.validerTilgangTilOrg(mockRequest, Mock.orgnrMedTilgang)
        }

        val result =
            messages
                .filter(EventName.TILGANG_ORG_REQUESTED)
                .filter(Key.TILGANG)
                .firstAsMap()

        val tilgang =
            result[Key.DATA]
                .shouldNotBeNull()
                .toMap()[Key.TILGANG]
                .shouldNotBeNull()
                .fromJson(Tilgang.serializer())

        tilgang shouldBe Tilgang.HAR_TILGANG
    }

    @Test
    fun `organisasjon - skal bli nektet tilgang`() {
        mockStatic(ApplicationRequest::lesFnrFraAuthToken) {
            every { mockRequest.lesFnrFraAuthToken() } returns Mock.innloggetFnr

            shouldThrowExactly<ManglerAltinnRettigheterException> {
                tilgangskontroll.validerTilgangTilOrg(mockRequest, Mock.orgnrUtenTilgang)
            }
        }

        val result =
            messages
                .filter(EventName.TILGANG_ORG_REQUESTED)
                .filter(Key.TILGANG)
                .firstAsMap()

        val tilgang =
            result[Key.DATA]
                .shouldNotBeNull()
                .toMap()[Key.TILGANG]
                .shouldNotBeNull()
                .fromJson(Tilgang.serializer())

        tilgang shouldBe Tilgang.IKKE_TILGANG
    }

    private object Mock {
        val innloggetFnr = Fnr.genererGyldig()

        val orgnrMedTilgang = Orgnr.genererGyldig()
        val orgnrUtenTilgang = Orgnr.genererGyldig()

        val forespoerselId: UUID = UUID.randomUUID()
    }
}
