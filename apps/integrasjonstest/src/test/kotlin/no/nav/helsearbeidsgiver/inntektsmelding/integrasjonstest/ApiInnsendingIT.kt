package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.verify
import kotlinx.serialization.builtins.serializer
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.test.mock.mockInnsending
import no.nav.hag.simba.utils.kontrakt.domene.bro.forespoersel.ForespoerselFraBro
import no.nav.hag.simba.utils.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.utils.kontrakt.domene.forespoersel.test.mockForespurtData
import no.nav.hag.simba.utils.kontrakt.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.dokarkiv.domene.OpprettOgFerdigstillResponse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.api.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.desember
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.kl
import no.nav.helsearbeidsgiver.utils.test.date.november
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiInnsendingIT : EndToEndTest() {
    @BeforeEach
    fun setup() {
        truncateDatabase()
    }

    @Test
    fun `skal ta imot ekstern inntektsmelding sendt gjennom sykepenge-APIet, validere inntekt mot a-ordning, ferdigstille sak og oppgave`() {
        mockForespoerselSvarFraHelsebro(
            forespoerselId = Mock.forespoerselId,
            forespoerselSvar = Mock.forespoerselSvar,
        )

        val inntektFraAordningen =
            mapOf(
                oktober(2017) to Mock.inntektBeloep,
                november(2017) to Mock.inntektBeloep.plus(10),
                desember(2017) to Mock.inntektBeloep.minus(10),
            )

        coEvery { inntektClient.hentInntektPerOrgnrOgMaaned(any(), any(), any(), any(), any()) } returns mapOf(Mock.orgnr.toString() to inntektFraAordningen)

        coEvery {
            dokarkivClient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any())
        } returns
            OpprettOgFerdigstillResponse(
                journalpostId = Mock.JOURNALPOST_ID,
                journalpostFerdigstilt = true,
                melding = "Ha en fin dag!",
                dokumenter = emptyList(),
            )

        publish(
            Key.EVENT_NAME to EventName.API_INNSENDING_STARTET.toJson(),
            Key.KONTEKST_ID to UUID.randomUUID().toJson(),
            Key.DATA to
                mapOf(
                    Key.INNSENDING to Mock.innsending.toJson(Innsending.serializer()),
                    Key.MOTTATT to Mock.mottatt.toJson(),
                ).toJson(),
        )

        messages
            .filter(EventName.API_INNSENDING_VALIDERT)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.INNSENDING]?.fromJson(Innsending.serializer()) shouldBe Mock.innsending
                data[Key.FORESPOERSEL_SVAR]?.fromJson(Forespoersel.serializer()) shouldBe Mock.forespoersel
            }

        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.ER_DUPLIKAT_IM)
            .firstAsMap()
            .also {
                // Ble lagret i databasen
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.ER_DUPLIKAT_IM].shouldNotBeNull().fromJson(Boolean.serializer()).shouldBeFalse()
            }

        messages
            .filter(EventName.INNTEKTSMELDING_MOTTATT)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
            }

        messages
            .filter(EventName.INNTEKTSMELDING_JOURNALFOERT)
            .firstAsMap()
            .also {
                it shouldContainKey Key.INNTEKTSMELDING
                it[Key.JOURNALPOST_ID]?.fromJson(String.serializer()) shouldBe Mock.JOURNALPOST_ID
            }

        messages
            .filter(EventName.INNTEKTSMELDING_JOURNALPOST_ID_LAGRET)
            .firstAsMap()
            .also {
                it shouldContainKey Key.INNTEKTSMELDING
                it[Key.JOURNALPOST_ID]?.fromJson(String.serializer()) shouldBe Mock.JOURNALPOST_ID
            }

        messages
            .filter(EventName.INNTEKTSMELDING_DISTRIBUERT)
            .firstAsMap()
            .also {
                // Verifiser at inntektsmelding er distribuert på ekstern kafka
                it[Key.JOURNALPOST_ID]?.fromJson(String.serializer()) shouldBe Mock.JOURNALPOST_ID

                it[Key.INNTEKTSMELDING].shouldNotBeNull()
            }

        messages
            .filter(EventName.SAK_OG_OPPGAVE_FERDIGSTILT)
            .firstAsMap()
            .also {
                Key.FORESPOERSEL_ID.les(UuidSerializer, it) shouldBe Mock.forespoerselId
            }

        bekreftMarkeringAvForespoerselSomBesvart()
    }

    private fun bekreftMarkeringAvForespoerselSomBesvart() {
        verify(exactly = 1) {
            producer.send(
                key = Mock.forespoerselId,
                message =
                    mapOf(
                        Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART_SIMBA.toJson(Pri.NotisType.serializer()),
                        Pri.Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                    ),
            )
        }
    }

    private object Mock {
        const val JOURNALPOST_ID = "journalpost-id-skoleboller"

        val mottatt = 29.oktober.kl(5, 44, 0, 0)

        val orgnr = Orgnr.genererGyldig()

        val inntektBeloep = 544.6
        val inntektsDato = 1.januar
        val inntekt = Inntekt(beloep = inntektBeloep, inntektsdato = inntektsDato, naturalytelser = emptyList(), endringAarsaker = emptyList())
        val innsending = mockInnsending().medInntekt(inntekt)
        val forespoerselId = innsending.skjema.forespoerselId

        val forespoersel =
            Forespoersel(
                orgnr = orgnr,
                fnr = Fnr.genererGyldig(),
                vedtaksperiodeId = UUID.randomUUID(),
                sykmeldingsperioder =
                    listOf(
                        1.januar til 22.januar,
                    ),
                egenmeldingsperioder = emptyList(),
                bestemmendeFravaersdager = mapOf(orgnr to 1.januar),
                forespurtData = mockForespurtData(),
                erBesvart = false,
                erBegrenset = false,
            )

        val forespoerselSvar =
            ForespoerselFraBro(
                orgnr = forespoersel.orgnr,
                fnr = forespoersel.fnr,
                forespoerselId = forespoerselId,
                vedtaksperiodeId = forespoersel.vedtaksperiodeId,
                egenmeldingsperioder = forespoersel.egenmeldingsperioder,
                sykmeldingsperioder = forespoersel.sykmeldingsperioder,
                bestemmendeFravaersdager = forespoersel.bestemmendeFravaersdager,
                forespurtData = mockForespurtData(),
                erBesvart = false,
                erBegrenset = false,
            )
    }
}

fun Innsending.medInntekt(inntekt: Inntekt): Innsending = this.copy(skjema = this.skjema.copy(inntekt = inntekt))
