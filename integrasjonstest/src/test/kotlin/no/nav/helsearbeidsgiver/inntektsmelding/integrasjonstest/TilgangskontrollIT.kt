package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Tilgang
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock.mockForespoerselSvarSuksess
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
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
            altinnClient.harRettighetForOrganisasjon(Mock.innloggetFnr.verdi, Mock.orgnrMedTilgang.verdi)
        } returns true

        coEvery {
            altinnClient.harRettighetForOrganisasjon(Mock.innloggetFnr.verdi, Mock.orgnrUtenTilgang.verdi)
        } returns false
    }

    @Test
    fun `forespoersel - skal få tilgang`() {
        val transaksjonId: UUID = UUID.randomUUID()

        mockForespoerselSvarFraHelsebro(
            forespoerselId = Mock.forespoerselId,
            forespoerselSvar =
                mockForespoerselSvarSuksess().copy(
                    forespoerselId = Mock.forespoerselId,
                    orgnr = Mock.orgnrMedTilgang,
                ),
        )

        tilgangProducer.publishForespoerselId(transaksjonId, Mock.innloggetFnr, Mock.forespoerselId)

        messages
            .filter(EventName.TILGANG_FORESPOERSEL_REQUESTED)
            .filter(BehovType.HENT_TRENGER_IM)
            .firstAsMap()
            .also {
                Key.UUID.lesOrNull(UuidSerializer, it) shouldBe transaksjonId

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
                    orgnr = Mock.orgnrUtenTilgang,
                ),
        )

        tilgangProducer.publishForespoerselId(UUID.randomUUID(), Mock.innloggetFnr, Mock.forespoerselId)

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
        tilgangProducer.publishOrgnr(UUID.randomUUID(), Mock.innloggetFnr, Mock.orgnrMedTilgang.verdi)

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
        tilgangProducer.publishOrgnr(UUID.randomUUID(), Mock.innloggetFnr, Mock.orgnrUtenTilgang.verdi)

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
