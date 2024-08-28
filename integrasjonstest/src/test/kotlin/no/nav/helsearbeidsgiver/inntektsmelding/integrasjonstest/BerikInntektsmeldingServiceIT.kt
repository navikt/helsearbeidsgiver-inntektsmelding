package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.dokarkiv.domene.OpprettOgFerdigstillResponse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.ForespoerselType
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.orgMapSerializer
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.json.lesBehov
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.bjarneBetjent
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.maxMekker
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.juli
import no.nav.helsearbeidsgiver.utils.test.date.juni
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

        val innsendingId = imRepository.lagreInntektsmeldingSkjema(Mock.forespoerselId, Mock.skjema)
        imRepository.oppdaterMedBeriketDokument(Mock.forespoerselId, innsendingId, tidligereInntektsmelding)

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
                    Key.ARBEIDSGIVER_FNR to Mock.fnrAg.toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to Mock.skjema.toJson(SkjemaInntektsmelding.serializer()),
                    Key.INNSENDING_ID to innsendingId.toJson(Long.serializer()),
                ).toJson(),
        )

        // Forespørsel hentet
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.FORESPOERSEL_SVAR, nestedData = true)
            .firstAsMap()
            .verifiserTransaksjonId(Mock.transaksjonId)
            .verifiserForespoerselIdFraSkjema()
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
            .verifiserForespoerselIdFraSkjema()
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
            .verifiserForespoerselIdFraSkjema()
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
            .verifiserForespoerselIdFraSkjema()
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
            .verifiserForespoerselIdFraSkjema()
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
    fun `skal opprette en bakgrunnsjobb som gjenopptar berikelsen av inntektsmeldingen senere dersom oppslaget mot pdl feiler`() {
        val tidligereInntektsmelding = mockInntektsmelding()

        forespoerselRepository.lagreForespoersel(Mock.forespoerselId.toString(), Mock.orgnr.toString())
        forespoerselRepository.oppdaterSakId(Mock.forespoerselId.toString(), Mock.SAK_ID)
        forespoerselRepository.oppdaterOppgaveId(Mock.forespoerselId.toString(), Mock.OPPGAVE_ID)
        val innsendingId = imRepository.lagreInntektsmeldingSkjema(Mock.forespoerselId, Mock.skjema)
        imRepository.oppdaterMedBeriketDokument(Mock.forespoerselId, innsendingId, tidligereInntektsmelding)

        coEvery {
            dokarkivClient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
        } returns
            OpprettOgFerdigstillResponse(
                journalpostId = "journalpost-id-granateple",
                journalpostFerdigstilt = true,
                melding = "Hvis du vil se bedre, bli en brilleslange!",
                dokumenter = emptyList(),
            )

        coEvery { pdlKlient.personBolk(any()) } throws RuntimeException("Fy fasan!") andThen
            listOf(
                bjarneBetjent,
                maxMekker,
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
                    Key.ARBEIDSGIVER_FNR to Mock.fnrAg.toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to Mock.skjema.toJson(SkjemaInntektsmelding.serializer()),
                    Key.INNSENDING_ID to innsendingId.toJson(Long.serializer()),
                ).toJson(),
        )

        // Forespørsel hentet
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.FORESPOERSEL_SVAR, nestedData = true)
            .firstAsMap()
            .verifiserTransaksjonId(Mock.transaksjonId)
            .verifiserForespoerselIdFraSkjema()
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
            .verifiserForespoerselIdFraSkjema()
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
            .verifiserForespoerselIdFraSkjema()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.VIRKSOMHETER]?.fromJson(orgMapSerializer) shouldBe mapOf(Mock.forespoersel.orgnr.let(::Orgnr) to "Bedrift A/S")
            }

        // Personnavn ikke hentet
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.PERSONER, nestedData = true)
            .all()
            .size shouldBe 0

        // Feilmelding
        messages.filterFeil().all().size shouldBe 1

        // Det blir satt opp en bakgrunnsjobb som kan gjenoppta berikelsen senere
        val bakgrunnsjobb = bakgrunnsjobbRepository.getById(Mock.transaksjonId)

        bakgrunnsjobb.also {
            it.shouldNotBeNull()
            it.data.parseJson().also {
                it.lesBehov() shouldBe BehovType.HENT_PERSONER
                it.toMap().verifiserForespoerselId().verifiserTransaksjonId(Mock.transaksjonId)
            }
        }

        // Gjenoppta berikelsen ved å kjøre den utløsende meldingen i bakgrunnsjobben
        bakgrunnsjobb?.data?.let { publish(it) }

        // Sykmeldt og innsender hentet
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.PERSONER, nestedData = true)
            .firstAsMap()
            .verifiserTransaksjonId(Mock.transaksjonId)
            .verifiserForespoerselIdFraSkjema()
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

    private fun Map<Key, JsonElement>.verifiserForespoerselIdFraSkjema(): Map<Key, JsonElement> =
        also {
            val data = it[Key.DATA]?.toMap().orEmpty()
            val skjema = Key.SKJEMA_INNTEKTSMELDING.lesOrNull(SkjemaInntektsmelding.serializer(), data)
            skjema?.forespoerselId shouldBe Mock.forespoerselId
        }

    private object Mock {
        val fnrAg = Fnr.genererGyldig()
        val orgnr = Orgnr.genererGyldig()
        val transaksjonId: UUID = UUID.randomUUID()
        const val SAK_ID = "tjukk-kalender"
        const val OPPGAVE_ID = "kunstig-demon"

        val skjema = mockSkjemaInntektsmelding()

        val forespoerselId: UUID = skjema.forespoerselId

        val forespoersel =
            Forespoersel(
                type = ForespoerselType.KOMPLETT,
                orgnr = orgnr.verdi,
                fnr = bjarneBetjent.ident!!,
                vedtaksperiodeId = UUID.randomUUID(),
                sykmeldingsperioder =
                    listOf(
                        1.juli til 12.juli,
                        15.juli til 2.august,
                    ),
                egenmeldingsperioder =
                    listOf(
                        26.juni til 27.juni,
                        29.juni til 29.juni,
                    ),
                bestemmendeFravaersdager = mapOf(orgnr.verdi to 15.juli),
                forespurtData = mockForespurtData(),
                erBesvart = false,
            )

        val forespoerselSvar =
            ForespoerselSvar.Suksess(
                type = ForespoerselType.KOMPLETT,
                orgnr = forespoersel.orgnr,
                fnr = forespoersel.fnr,
                vedtaksperiodeId = forespoersel.vedtaksperiodeId,
                egenmeldingsperioder = forespoersel.egenmeldingsperioder,
                sykmeldingsperioder = forespoersel.sykmeldingsperioder,
                skjaeringstidspunkt = null,
                bestemmendeFravaersdager = forespoersel.bestemmendeFravaersdager,
                forespurtData = mockForespurtData(),
                erBesvart = false,
            )
    }
}
