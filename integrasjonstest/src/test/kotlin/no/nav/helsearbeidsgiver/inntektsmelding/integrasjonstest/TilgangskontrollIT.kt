package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TilgangskontrollIT : EndToEndTest() {

    @BeforeAll
    fun beforeAll() {
        forespoerselRepository.lagreForespoersel(Mock.forespoerselIdMedTilgang.toString(), Mock.ORGNR_MED_TILGANG)
        forespoerselRepository.lagreForespoersel(Mock.forespoerselIdUtenTilgang.toString(), Mock.ORGNR_UTEN_TILGANG)
    }

    @BeforeEach
    fun beforeEach() {
        coEvery {
            altinnClient.harRettighetForOrganisasjon(Mock.INNLOGGET_FNR, Mock.ORGNR_MED_TILGANG)
        } returns true

        coEvery {
            altinnClient.harRettighetForOrganisasjon(Mock.INNLOGGET_FNR, Mock.ORGNR_UTEN_TILGANG)
        } returns false
    }

    @Test
    fun `skal få tilgang`() {
        tilgangProducer.publish(Mock.forespoerselIdMedTilgang, Mock.INNLOGGET_FNR)

        Thread.sleep(6000)

        val result = messages.filter(EventName.TILGANG_REQUESTED)
            .filter(Key.TILGANG)
            .firstAsMap()

        val tilgang = result[Key.TILGANG]
            .shouldNotBeNull()
            .fromJson(Tilgang.serializer())

        tilgang shouldBe Tilgang.HAR_TILGANG
    }

    @Test
    fun `skal bli nektet tilgang`() {
        tilgangProducer.publish(Mock.forespoerselIdUtenTilgang, Mock.INNLOGGET_FNR)

        Thread.sleep(4000)

        val result = messages.filter(EventName.TILGANG_REQUESTED)
            .filter(Key.TILGANG)
            .firstAsMap()

        val tilgang = result[Key.TILGANG]
            .shouldNotBeNull()
            .fromJson(Tilgang.serializer())

        tilgang shouldBe Tilgang.IKKE_TILGANG
    }

    @Test
    fun `skal få melding om at forespørsel ikke finnes`() {
        tilgangProducer.publish(Mock.forespoerselIdFinnesIkke, Mock.INNLOGGET_FNR)

        Thread.sleep(4000)

        val fail = messages.filterFeil()
            .firstAsMap()
            .get(Key.FAIL)
            .shouldNotBeNull()
            .fromJson(Fail.serializer())

        fail.feilmelding shouldBe "Fant ingen orgnr for forespørsel-ID '${Mock.forespoerselIdFinnesIkke}'."
    }

    private object Mock {
        const val INNLOGGET_FNR = "10436700000"

        const val ORGNR_MED_TILGANG = "654654654"
        const val ORGNR_UTEN_TILGANG = "789789789"

        val forespoerselIdMedTilgang: UUID = UUID.randomUUID()
        val forespoerselIdUtenTilgang: UUID = UUID.randomUUID()
        val forespoerselIdFinnesIkke: UUID = UUID.randomUUID()
    }
}
