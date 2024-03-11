package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.test.mock.mockTrengerInntekt
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TilgangskontrollIT : EndToEndTest() {

    @BeforeEach
    fun beforeEach() {
        clearAllMocks()

        coEvery {
            altinnClient.harRettighetForOrganisasjon(Mock.INNLOGGET_FNR, Mock.ORGNR_MED_TILGANG)
        } returns true

        coEvery {
            altinnClient.harRettighetForOrganisasjon(Mock.INNLOGGET_FNR, Mock.ORGNR_UTEN_TILGANG)
        } returns false
    }

    @Test
    fun `forespoersel - skal få tilgang`() {
        val transaksjonId: UUID = UUID.randomUUID()

        mockForespoerselSvarFraHelsebro(
            eventName = EventName.TILGANG_FORESPOERSEL_REQUESTED,
            transaksjonId = transaksjonId,
            forespoerselId = Mock.forespoerselId,
            forespoersel = mockTrengerInntekt().copy(
                orgnr = Mock.ORGNR_MED_TILGANG
            )
        )

        mockStatic(::randomUuid) {
            every { randomUuid() } returns transaksjonId

            tilgangProducer.publishForespoerselId(Mock.forespoerselId, Mock.INNLOGGET_FNR)
        }

        messages.filter(EventName.TILGANG_FORESPOERSEL_REQUESTED)
            .filter(BehovType.HENT_TRENGER_IM)
            .firstAsMap()
            .also {
                Key.UUID.lesOrNull(UuidSerializer, it) shouldBe transaksjonId
                Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, it) shouldBe Mock.forespoerselId
            }

        val result = messages.filter(EventName.TILGANG_FORESPOERSEL_REQUESTED)
            .filter(Key.TILGANG)
            .firstAsMap()

        val tilgang = result[Key.TILGANG]
            .shouldNotBeNull()
            .fromJson(Tilgang.serializer())

        tilgang shouldBe Tilgang.HAR_TILGANG
    }

    @Test
    fun `forespoersel - skal bli nektet tilgang`() {
        val transaksjonId: UUID = UUID.randomUUID()

        mockForespoerselSvarFraHelsebro(
            eventName = EventName.TILGANG_FORESPOERSEL_REQUESTED,
            transaksjonId = transaksjonId,
            forespoerselId = Mock.forespoerselId,
            forespoersel = mockTrengerInntekt().copy(
                orgnr = Mock.ORGNR_UTEN_TILGANG
            )
        )

        mockStatic(::randomUuid) {
            every { randomUuid() } returns transaksjonId

            tilgangProducer.publishForespoerselId(Mock.forespoerselId, Mock.INNLOGGET_FNR)
        }

        val result = messages.filter(EventName.TILGANG_FORESPOERSEL_REQUESTED)
            .filter(Key.TILGANG)
            .firstAsMap()

        val tilgang = result[Key.TILGANG]
            .shouldNotBeNull()
            .fromJson(Tilgang.serializer())

        tilgang shouldBe Tilgang.IKKE_TILGANG
    }

    @Test
    fun `organisasjon - skal få tilgang`() {
        tilgangProducer.publishOrgnr(Mock.ORGNR_MED_TILGANG, Mock.INNLOGGET_FNR)

        val result = messages.filter(EventName.TILGANG_ORG_REQUESTED)
            .filter(Key.TILGANG)
            .firstAsMap()

        val tilgang = result[Key.TILGANG]
            .shouldNotBeNull()
            .fromJson(Tilgang.serializer())

        tilgang shouldBe Tilgang.HAR_TILGANG
    }

    @Test
    fun `organisasjon - skal bli nektet tilgang`() {
        tilgangProducer.publishOrgnr(Mock.ORGNR_UTEN_TILGANG, Mock.INNLOGGET_FNR)

        val result = messages.filter(EventName.TILGANG_ORG_REQUESTED)
            .filter(Key.TILGANG)
            .firstAsMap()

        val tilgang = result[Key.TILGANG]
            .shouldNotBeNull()
            .fromJson(Tilgang.serializer())

        tilgang shouldBe Tilgang.IKKE_TILGANG
    }

    private object Mock {
        const val INNLOGGET_FNR = "10436700000"

        const val ORGNR_MED_TILGANG = "654654654"
        const val ORGNR_UTEN_TILGANG = "789789789"

        val forespoerselId: UUID = UUID.randomUUID()
    }
}
