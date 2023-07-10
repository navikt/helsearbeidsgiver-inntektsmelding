package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.HentImOrgnrLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.TilgangskontrollLøsning
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyKeys
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.lesLoesning
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TilgangskontrollIT : EndToEndTest() {

    @BeforeAll
    fun beforeAll() {
        forespoerselRepository.lagreForespørsel(Mock.FORESPOERSEL_ID_MED_TILGANG, Mock.ORGNR_MED_TILGANG)
        forespoerselRepository.lagreForespørsel(Mock.FORESPOERSEL_ID_UTEN_TILGANG, Mock.ORGNR_UTEN_TILGANG)
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
        tilgangProducer.publish(Mock.INNLOGGET_FNR, Mock.FORESPOERSEL_ID_MED_TILGANG)

        Thread.sleep(6000)

        val result = messages.filter(EventName.HENT_PREUTFYLT)
            .filter(BehovType.TILGANGSKONTROLL, loesningPaakrevd = true)
            .first()
            .fromJsonMapOnlyKeys()

        result[Key.BEHOV]?.fromJson(BehovType.serializer().list())
            .shouldNotBeNull()
            .shouldContain(BehovType.HENT_IM_ORGNR)

        val loesning = result.lesLoesning(BehovType.TILGANGSKONTROLL, TilgangskontrollLøsning.serializer())

        loesning?.value shouldBe Tilgang.HAR_TILGANG
    }

    @Test
    fun `skal bli nektet tilgang`() {
        tilgangProducer.publish(Mock.INNLOGGET_FNR, Mock.FORESPOERSEL_ID_UTEN_TILGANG)

        Thread.sleep(4000)

        val result = messages.filter(EventName.HENT_PREUTFYLT)
            .filter(BehovType.TILGANGSKONTROLL, loesningPaakrevd = true)
            .first()
            .fromJsonMapOnlyKeys()

        result[Key.BEHOV]?.fromJson(BehovType.serializer().list())
            .shouldNotBeNull()
            .shouldContain(BehovType.HENT_IM_ORGNR)

        val loesning = result.lesLoesning(BehovType.TILGANGSKONTROLL, TilgangskontrollLøsning.serializer())

        loesning?.value shouldBe Tilgang.IKKE_TILGANG
    }

    @Test
    fun `skal få melding om at forespørsel ikke finnes`() {
        tilgangProducer.publish(Mock.INNLOGGET_FNR, Mock.FORESPOERSEL_ID_FINNES_IKKE)

        Thread.sleep(4000)

        val result = messages.filter(EventName.HENT_PREUTFYLT)
            .filter(BehovType.HENT_IM_ORGNR, loesningPaakrevd = true)
            .first()
            .fromJsonMapOnlyKeys()

        result[Key.BEHOV]?.fromJson(BehovType.serializer().list())
            .shouldNotBeNull()
            .shouldContain(BehovType.HENT_IM_ORGNR)

        val loesning = result.lesLoesning(BehovType.HENT_IM_ORGNR, HentImOrgnrLøsning.serializer())

        loesning?.error?.melding shouldBe "Fant ikke forespørselId ${Mock.FORESPOERSEL_ID_FINNES_IKKE}"
    }

    private object Mock {
        const val INNLOGGET_FNR = "fnr-456"

        const val ORGNR_MED_TILGANG = "org-456"
        const val ORGNR_UTEN_TILGANG = "org-789"

        val FORESPOERSEL_ID_MED_TILGANG = UUID.randomUUID().toString()
        val FORESPOERSEL_ID_UTEN_TILGANG = UUID.randomUUID().toString()
        val FORESPOERSEL_ID_FINNES_IKKE = UUID.randomUUID().toString()
    }
}
