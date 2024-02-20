@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.prometheus.client.CollectorRegistry
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.brreg.Virksomhet
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.json.readFail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class VirksomhetLoeserTest {

    private val testRapid = TestRapid()
    private val mockBrregClient = mockk<BrregClient>()

    private val ORGNR = "orgnr-1"
    private val VIRKSOMHET_NAVN = "Norge AS"

    init {
        VirksomhetLoeser(testRapid, mockBrregClient, false)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        CollectorRegistry.defaultRegistry.clear() // prometheus metrics
    }

    @Test
    fun `skal håndtere at klient feiler`() {
        coEvery { mockBrregClient.hentVirksomhetNavn(any()) } returns null
        coEvery { mockBrregClient.hentVirksomheter(any()) } returns emptyList()

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
            Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
            Key.ORGNRUNDERENHET to ORGNR.toJson(),
            Key.UUID to UUID.randomUUID().toJson()
        )

        val publisert = testRapid.firstMessage().readFail()

        publisert.feilmelding shouldBe "Fant ikke virksomhet"
    }

    @Test
    fun `skal returnere løsning når gyldige data`() {
        coEvery { mockBrregClient.hentVirksomhetNavn(any()) } returns VIRKSOMHET_NAVN
        coEvery { mockBrregClient.hentVirksomheter(any()) } returns listOf(Virksomhet(organisasjonsnummer = ORGNR, navn = VIRKSOMHET_NAVN))

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
            Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
            Key.ORGNRUNDERENHET to ORGNR.toJson(),
            Key.UUID to UUID.randomUUID().toJson()
        )

        val publisert = testRapid.firstMessage().toMap()

        publisert[Key.VIRKSOMHET]
            .shouldNotBeNull()
            .fromJson(String.serializer())
            .shouldBe(VIRKSOMHET_NAVN)
    }

    @Test
    fun `skal håndtere ukjente feil`() {
        testRapid.sendJson(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
            Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
            Key.ORGNRUNDERENHET to ORGNR.toJson(),
            Key.UUID to UUID.randomUUID().toJson()
        )

        val publisert = testRapid.firstMessage()

        publisert.readFail().feilmelding shouldBe "Klarte ikke hente virksomhet"
    }
}
