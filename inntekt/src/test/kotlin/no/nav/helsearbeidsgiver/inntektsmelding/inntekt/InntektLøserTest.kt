@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import com.fasterxml.jackson.databind.JsonNode
import io.mockk.coEvery
import io.mockk.coVerifySequence
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.HentTrengerImLøsning
import no.nav.helsearbeidsgiver.felles.InntektLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Periode
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.felles.til
import no.nav.helsearbeidsgiver.inntekt.ArbeidsInntektInformasjon
import no.nav.helsearbeidsgiver.inntekt.ArbeidsinntektMaaned
import no.nav.helsearbeidsgiver.inntekt.Ident
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.inntekt.InntektskomponentResponse
import no.nav.helsearbeidsgiver.inntekt.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.juli
import no.nav.helsearbeidsgiver.utils.test.date.juni
import no.nav.helsearbeidsgiver.utils.test.date.mai
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.resource.readResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import no.nav.helsearbeidsgiver.inntekt.Inntekt as Arbeidsinntekt

private const val ORGNR = "123456789"

class InntektLøserTest {

    private val rapid = TestRapid()
    private val inntektKlient: InntektKlient = mockk()

    init {
        InntektLøser(rapid, inntektKlient)
    }

    @BeforeEach
    fun beforeEach() {
        rapid.reset()
    }

    @Test
    fun `skal håndtere feil mot inntektskomponenten`() {
        coEvery {
            inntektKlient.hentInntektListe(any(), any(), any(), any(), any(), any(), any())
        } throws RuntimeException()

        sendBehovTilLøser()
        val løsning: JsonNode = rapid.inspektør.message(0).path("@løsning")
        val inntektLøsning = løsning.get(BehovType.INNTEKT.name)?.toJsonElement()?.fromJson(InntektLøsning.serializer())
        assertNull(inntektLøsning?.value)
        assertNotNull(inntektLøsning?.error)
        assertEquals("Klarte ikke hente inntekt", inntektLøsning?.error?.melding)
    }

    @Test
    fun `skal publisere svar fra inntektskomponenten`() {
        val response = "response.json".readResource().fromJson(InntektskomponentResponse.serializer())
        coEvery {
            inntektKlient.hentInntektListe(any(), any(), any(), any(), any(), any(), any())
        } returns response
        sendBehovTilLøser()
        val løsning: JsonNode = rapid.inspektør.message(0).path("@løsning")
        val inntektLøsning = løsning.get(BehovType.INNTEKT.name)?.toJsonElement()?.fromJson(InntektLøsning.serializer())
        assertNull(inntektLøsning?.error)
        assertNotNull(inntektLøsning?.value)
    }

    @Test
    fun `bruker dato fra request dersom denne kommer (oppdater skjema med ny inntekt om fravær endres)`() {
        val response = "response.json".readResource().fromJson(InntektskomponentResponse.serializer())
        coEvery {
            inntektKlient.hentInntektListe(any(), any(), any(), any(), any(), any(), any())
        } returns response
        sendBehovForOppdatertInntektTilLøser()
        val løsning: JsonNode = rapid.inspektør.message(0).path("@løsning")
        val inntektLøsning = løsning.get(BehovType.INNTEKT.name)?.toJsonElement()?.fromJson(InntektLøsning.serializer())
        assertNull(inntektLøsning?.error)
        assertNotNull(inntektLøsning?.value)
    }

    @Test
    fun `egenmeldinger påvirker hvilke inntektsmåneder som hentes`() {
        val response = "response.json".readResource().fromJson(InntektskomponentResponse.serializer())

        coEvery {
            inntektKlient.hentInntektListe(any(), any(), any(), any(), any(), any(), any())
        } returns response

        rapid.sendJson(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
            Key.BEHOV to listOf(BehovType.FULLT_NAVN, BehovType.INNTEKT).toJson(BehovType.serializer()),
            Key.ID to UUID.randomUUID().toJson(),
            Key.UUID to "uuid".toJson(),
            Key.SESSION to sessionDataJson(
                orgnr = "123456785",
                sykmeldingsperioder = listOf(1.juli(2020) til 31.juli(2020)),
                egenmeldingsperioder = listOf(30.juni(2020) til 30.juni(2020))
            )
        )

        coVerifySequence {
            inntektKlient.hentInntektListe(any(), any(), any(), fraOgMed = 1.mars(2020), tilOgMed = 31.mai(2020), any(), any())
        }
    }

    @Test
    fun `skal benytte nyeste hvis flere sykmeldingsperioder`() {
        val inntektPeriode = finnInntektPeriode(
            listOf(
                (1.januar til 20.januar), // år 2018 er default
                (1.februar til 20.februar)
            )
        )
        // Skal velge februar, dermed blir tremånedersperioden november - januar
        val expectedFom = LocalDate.of(2017, 11, 1)
        val expectedTom = LocalDate.of(2018, 1, 31)
        assertEquals(expectedFom, inntektPeriode.fom)
        assertEquals(expectedTom, inntektPeriode.tom)
    }

    @Test
    fun `skal slå sammen perioder som er sammenhengende`() {
        val p1 = 1.februar til 6.februar
        val p2 = 7.februar til 19.februar
        val perioder = listOf(p2, p1)
        val skjæringstidspunkt = finnSkjæringstidspunkt(perioder)
        assertEquals(p1.fom, skjæringstidspunkt)
    }

    @Test
    fun `skal finne riktig inntekt basert på sykmeldingsperioden som kommer i BEHOV`() {
        // Denne testen er litt grisete og tester mest egen mocke-logikk, men beholder foreløpig
        val inntektSvar = List(12) {
            lagInntektMaaned(YearMonth.of(2022, 12).minusMonths(it.toLong()))
        }
        val fom = slot<LocalDate>()
        val tom = slot<LocalDate>()
        coEvery {
            inntektKlient.hentInntektListe(
                ident = any(),
                callId = any(),
                navConsumerId = any(),
                fraOgMed = capture(fom),
                tilOgMed = capture(tom),
                filter = any(),
                formaal = any()
            )
        } answers {
            val fomMåned = YearMonth.from(fom.captured)
            val tomMåned = YearMonth.from(tom.captured)
            val mellomFomOgTom = inntektSvar.filterNot {
                it.aarMaaned == null ||
                    it.aarMaaned!!.isBefore(fomMåned) ||
                    it.aarMaaned!!.isAfter(tomMåned)
            }
            InntektskomponentResponse(mellomFomOgTom)
        }
        sendBehovTilLøser()

        val løsning: JsonNode = rapid.inspektør.message(0).path("@løsning")
        val inntektLøsning = løsning.get(BehovType.INNTEKT.name)?.toJsonElement()?.fromJson(InntektLøsning.serializer())
        assertNull(inntektLøsning?.error)
        assertEquals(3, inntektLøsning?.value!!.historisk.size)

        val maaneder = inntektLøsning.value!!.historisk.map { it.maaned }

        assertEquals(april(2022), maaneder[0])
        assertEquals(mars(2022), maaneder[1])
        assertEquals(februar(2022), maaneder[2])
    }

    private fun sendBehovTilLøser() {
        rapid.sendJson(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(EventName.serializer()),
            Key.BEHOV to listOf(BehovType.FULLT_NAVN, BehovType.INNTEKT).toJson(BehovType.serializer()),
            Key.ID to UUID.randomUUID().toJson(),
            Key.UUID to "uuid".toJson(),
            Key.SESSION to sessionDataJson()
        )
    }

    // TODO: dropp send, bare bygg opp pakke. Lag feil-tester for uten orgnr og id,
    //  samt mangler både inntekt_dato og sykmeldingperiode
    private fun sendBehovForOppdatertInntektTilLøser() {
        rapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKT_REQUESTED.toJson(),
            Key.BEHOV to listOf(BehovType.INNTEKT).toJson(BehovType.serializer()),
            Key.ID to UUID.randomUUID().toJson(),
            Key.UUID to "uuid".toJson(),
            Key.SESSION to sessionDataJson(),
            Key.BOOMERANG to mapOf(
                Key.INNTEKT_DATO.str to LocalDate.of(2022, 10, 30).toJson(LocalDateSerializer)
            ).toJson()
        )
    }

    private fun sessionDataJson(
        orgnr: String = ORGNR,
        sykmeldingsperioder: List<Periode> = listOf(2.mai(2022) til 16.mai(2022)),
        egenmeldingsperioder: List<Periode> = listOf(1.mai(2022) til 1.mai(2022))
    ): JsonElement = mapOf(
        BehovType.HENT_TRENGER_IM to HentTrengerImLøsning(
            TrengerInntekt(
                fnr = "fnr",
                orgnr = orgnr,
                sykmeldingsperioder = sykmeldingsperioder,
                egenmeldingsperioder = egenmeldingsperioder,
                forespurtData = mockForespurtData()
            )
        )
    ).toJson(
        MapSerializer(
            BehovType.serializer(),
            HentTrengerImLøsning.serializer()
        )
    )

    private fun lagInntektMaaned(mnd: YearMonth): ArbeidsinntektMaaned {
        return ArbeidsinntektMaaned(
            aarMaaned = mnd,
            arbeidsInntektInformasjon = ArbeidsInntektInformasjon(
                inntektListe = listOf(
                    Arbeidsinntekt(
                        beloep = 1.0,
                        virksomhet = Ident(ORGNR)
                    )
                )
            )
        )
    }
}
