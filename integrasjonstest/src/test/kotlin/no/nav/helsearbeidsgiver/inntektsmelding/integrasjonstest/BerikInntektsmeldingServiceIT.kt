package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.dokarkiv.domene.OpprettOgFerdigstillResponse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Forespoersel
import no.nav.helsearbeidsgiver.felles.ForespoerselType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.orgMapSerializer
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.mock.gyldigInnsendingRequest
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.toForespoersel
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BerikInntektsmeldingServiceIT : EndToEndTest() {
    @BeforeEach
    fun setup() {
        truncateDatabase()
    }

    @Test
    fun `skal berike og lagre inntektsmeldinger`() {
        val tidligereInntektsmelding = mockInntektsmelding()

        forespoerselRepository.lagreForespoersel(Mock.forespoerselId.toString(), Mock.orgnr.toString())
        forespoerselRepository.oppdaterSakId(Mock.forespoerselId.toString(), Mock.SAK_ID)
        forespoerselRepository.oppdaterOppgaveId(Mock.forespoerselId.toString(), Mock.OPPGAVE_ID)
        imRepository.lagreInntektsmelding(Mock.forespoerselId, tidligereInntektsmelding)

        coEvery {
            dokarkivClient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
        } returns
            OpprettOgFerdigstillResponse(
                journalpostId = "journalpost-id-sukkerspinn",
                journalpostFerdigstilt = true,
                melding = "Ha en brillefin dag!",
                dokumenter = emptyList(),
            )

        mockForespoerselSvarFraHelsebro(
            forespoerselId = Mock.forespoerselId,
            forespoerselSvar = Mock.forespoerselSvar,
        )

        publish(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_SKJEMA_LAGRET.toJson(),
            Key.UUID to Mock.transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                    Key.ARBEIDSGIVER_FNR to Mock.fnrAg.toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to gyldigInnsendingRequest.toJson(Innsending.serializer()),
                ).toJson(),
        )

        // Forespørsel hentet
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.FORESPOERSEL_SVAR, nestedData = true)
            .firstAsMap()
            .verifiserTransaksjonId(Mock.transaksjonId)
            .verifiserForespoerselId()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.FORESPOERSEL_SVAR]?.fromJson(Forespoersel.serializer()) shouldBe Mock.forespoersel
            }

        // Tidligere inntektsmelding hentet
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.LAGRET_INNTEKTSMELDING, nestedData = true)
            .filter(Key.EKSTERN_INNTEKTSMELDING, nestedData = true)
            .firstAsMap()
            .verifiserTransaksjonId(Mock.transaksjonId)
            .verifiserForespoerselId()
            .also {
                it shouldContainKey Key.DATA

                val data = it[Key.DATA].shouldNotBeNull().toMap()
                val tidligereInntektsmeldingResult = ResultJson(success = tidligereInntektsmelding.toJson(Inntektsmelding.serializer()))

                data[Key.LAGRET_INNTEKTSMELDING]?.fromJson(ResultJson.serializer()) shouldBe tidligereInntektsmeldingResult
                data[Key.EKSTERN_INNTEKTSMELDING]?.fromJson(ResultJson.serializer()) shouldBe ResultJson(success = null)
            }

        // Virksomhetsnavn hentet
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.VIRKSOMHETER, nestedData = true)
            .firstAsMap()
            .verifiserTransaksjonId(Mock.transaksjonId)
            .verifiserForespoerselId()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.VIRKSOMHETER]?.fromJson(orgMapSerializer) shouldBe mapOf(Mock.forespoersel.orgnr.let(::Orgnr) to "Bedrift A/S")
            }

        // Inntektsmelding lagret
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.INNTEKTSMELDING, nestedData = true)
            .filter(Key.ER_DUPLIKAT_IM, nestedData = true)
            .firstAsMap()
            .verifiserTransaksjonId(Mock.transaksjonId)
            .verifiserForespoerselId()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()

                data[Key.INNTEKTSMELDING].shouldNotBeNull().fromJson(Inntektsmelding.serializer())

                data[Key.ER_DUPLIKAT_IM]?.fromJson(Boolean.serializer()) shouldBe false
            }

        // Sykmeldt og innsender hentet
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.PERSONER, nestedData = true)
            .firstAsMap()
            .verifiserTransaksjonId(Mock.transaksjonId)
            .verifiserForespoerselId()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()

                shouldNotThrowAny {
                    data[Key.PERSONER]
                        .shouldNotBeNull()
                        .fromJson(personMapSerializer)
                }
            }

        // Siste melding fra service
        messages
            .filter(EventName.INNTEKTSMELDING_MOTTATT)
            .firstAsMap()
            .verifiserTransaksjonId(Mock.transaksjonId)
            .verifiserForespoerselId()
            .also {
                shouldNotThrowAny {
                    it[Key.UUID]
                        .shouldNotBeNull()
                        .fromJson(UuidSerializer)

                    it[Key.FORESPOERSEL_ID]
                        .shouldNotBeNull()
                        .fromJson(UuidSerializer)

                    it[Key.INNTEKTSMELDING_DOKUMENT]
                        .shouldNotBeNull()
                        .fromJson(Inntektsmelding.serializer())
                }
            }

        // Ingen feil
        messages.filterFeil().all().size shouldBe 0
    }

    @Test
    fun `skal bruke defaultverdi for virksomhetsnavn dersom kall til brreg og pdl feiler`() {
        val tidligereInntektsmelding = mockInntektsmelding()

        forespoerselRepository.lagreForespoersel(Mock.forespoerselId.toString(), Mock.orgnr.toString())
        forespoerselRepository.oppdaterSakId(Mock.forespoerselId.toString(), Mock.SAK_ID)
        forespoerselRepository.oppdaterOppgaveId(Mock.forespoerselId.toString(), Mock.OPPGAVE_ID)
        imRepository.lagreInntektsmelding(Mock.forespoerselId, tidligereInntektsmelding)

        coEvery {
            dokarkivClient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
        } returns
            OpprettOgFerdigstillResponse(
                journalpostId = "journalpost-id-sukkerspinn",
                journalpostFerdigstilt = true,
                melding = "Ha en brillefin dag!",
                dokumenter = emptyList(),
            )

        coEvery { brregClient.hentVirksomheter(any()) } answers {
            throw RuntimeException("Fy fasan!")
        }

        coEvery { pdlKlient.personBolk(any()) } answers {
            throw RuntimeException("Jai ikke gidde tid til å spille mere sjakk med den dumme ape!")
        }

        mockForespoerselSvarFraHelsebro(
            forespoerselId = Mock.forespoerselId,
            forespoerselSvar = Mock.forespoerselSvar,
        )

        publish(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_SKJEMA_LAGRET.toJson(),
            Key.UUID to Mock.transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                    Key.ARBEIDSGIVER_FNR to Mock.fnrAg.toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to gyldigInnsendingRequest.toJson(Innsending.serializer()),
                ).toJson(),
        )

        // Virksomhetsnavn blir satt default tomt
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.VIRKSOMHETER, nestedData = true)
            .firstAsMap()
            .verifiserTransaksjonId(Mock.transaksjonId)
            .verifiserForespoerselId()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                shouldNotThrowAny {
                    data[Key.VIRKSOMHETER]
                        ?.fromJson(orgMapSerializer)
                        ?.shouldBeEmpty()
                }
            }

        // Sykmeldt og innsender blir satt default tomt
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.PERSONER, nestedData = true)
            .firstAsMap()
            .verifiserTransaksjonId(Mock.transaksjonId)
            .verifiserForespoerselId()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()

                shouldNotThrowAny {
                    data[Key.PERSONER]
                        .shouldNotBeNull()
                        .fromJson(personMapSerializer)
                        .shouldBeEmpty()
                }
            }

        // Siste melding fra service blir sendt
        messages
            .filter(EventName.INNTEKTSMELDING_MOTTATT)
            .firstAsMap()
            .verifiserTransaksjonId(Mock.transaksjonId)
            .verifiserForespoerselId()
            .also {
                shouldNotThrowAny {
                    it[Key.UUID]
                        .shouldNotBeNull()
                        .fromJson(UuidSerializer)

                    it[Key.FORESPOERSEL_ID]
                        .shouldNotBeNull()
                        .fromJson(UuidSerializer)

                    it[Key.INNTEKTSMELDING_DOKUMENT]
                        .shouldNotBeNull()
                        .fromJson(Inntektsmelding.serializer())
                }
            }
    }

    private fun Map<Key, JsonElement>.verifiserTransaksjonId(transaksjonId: UUID): Map<Key, JsonElement> =
        also {
            Key.UUID.lesOrNull(UuidSerializer, it) shouldBe transaksjonId
        }

    private fun Map<Key, JsonElement>.verifiserForespoerselId(): Map<Key, JsonElement> =
        also {
            val data = it[Key.DATA]?.toMap().orEmpty()
            val forespoerselId = Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, it) ?: Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, data)
            forespoerselId shouldBe Mock.forespoerselId
        }

    private object Mock {
        val fnrAg = Fnr.genererGyldig()
        val orgnr = Orgnr.genererGyldig()
        val transaksjonId: UUID = UUID.randomUUID()
        const val SAK_ID = "tjukk-kalender"
        const val OPPGAVE_ID = "kunstig-demon"

        val forespoerselId: UUID = UUID.randomUUID()
        val vedtaksperiodeId: UUID = UUID.randomUUID()

        val forespoerselSvar =
            ForespoerselSvar.Suksess(
                type = ForespoerselType.KOMPLETT,
                orgnr = gyldigInnsendingRequest.orgnrUnderenhet,
                fnr = gyldigInnsendingRequest.identitetsnummer,
                vedtaksperiodeId = vedtaksperiodeId,
                egenmeldingsperioder = gyldigInnsendingRequest.egenmeldingsperioder,
                sykmeldingsperioder = gyldigInnsendingRequest.fraværsperioder,
                skjaeringstidspunkt = gyldigInnsendingRequest.bestemmendeFraværsdag,
                bestemmendeFravaersdager =
                    gyldigInnsendingRequest.fraværsperioder
                        .lastOrNull()
                        ?.let { mapOf(gyldigInnsendingRequest.orgnrUnderenhet to it.fom) }
                        .orEmpty(),
                forespurtData = mockForespurtData(),
                erBesvart = false,
            )

        val forespoersel = forespoerselSvar.toForespoersel()
    }
}
