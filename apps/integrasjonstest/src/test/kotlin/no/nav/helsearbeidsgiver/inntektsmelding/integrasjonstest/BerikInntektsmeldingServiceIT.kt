package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.dokarkiv.domene.OpprettOgFerdigstillResponse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.ForespoerselFraBro
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.orgMapSerializer
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.json.lesBehov
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingGammeltFormat
import no.nav.helsearbeidsgiver.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.bjarneBetjent
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.maxMekker
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.desember
import no.nav.helsearbeidsgiver.utils.test.date.juli
import no.nav.helsearbeidsgiver.utils.test.date.juni
import no.nav.helsearbeidsgiver.utils.test.date.kl
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
        val tidligereInntektsmelding = mockInntektsmeldingGammeltFormat()

        val innsendingId = imRepository.lagreInntektsmeldingSkjema(Mock.skjema, 10.desember.atStartOfDay())
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
            Key.KONTEKST_ID to Mock.transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.ARBEIDSGIVER_FNR to Mock.fnrAg.toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to Mock.skjema.toJson(SkjemaInntektsmelding.serializer()),
                    Key.INNSENDING_ID to innsendingId.toJson(Long.serializer()),
                    Key.MOTTATT to Mock.mottatt.toJson(),
                ).toJson(),
        )

        // Forespørsel hentet
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.FORESPOERSEL_SVAR)
            .firstAsMap()
            .verifiserTransaksjonId(Mock.transaksjonId)
            .verifiserForespoerselIdFraSkjema()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.FORESPOERSEL_SVAR]?.fromJson(Forespoersel.serializer()) shouldBe Mock.forespoersel
            }

        // Virksomhetsnavn hentet
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.VIRKSOMHETER)
            .firstAsMap()
            .verifiserTransaksjonId(Mock.transaksjonId)
            .verifiserForespoerselIdFraSkjema()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.VIRKSOMHETER]?.fromJson(orgMapSerializer) shouldBe mapOf(Mock.forespoersel.orgnr to "Bedrift A/S")
            }

        // Inntektsmelding lagret
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.ER_DUPLIKAT_IM)
            .firstAsMap()
            .verifiserTransaksjonId(Mock.transaksjonId)
            .verifiserForespoerselIdFraSkjema()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.ER_DUPLIKAT_IM]?.fromJson(Boolean.serializer()) shouldBe false
            }

        // Sykmeldt og innsender hentet
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.PERSONER)
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
                    it[Key.KONTEKST_ID]
                        .shouldNotBeNull()
                        .fromJson(UuidSerializer)

                    val data = it[Key.DATA].shouldNotBeNull().toMap()

                    data[Key.FORESPOERSEL_ID]
                        .shouldNotBeNull()
                        .fromJson(UuidSerializer)

                    data[Key.INNTEKTSMELDING]
                        .shouldNotBeNull()
                        .fromJson(Inntektsmelding.serializer())
                }
            }

        // Ingen feil
        messages.filterFeil().all().size shouldBe 0
    }

    @Test
    fun `skal opprette en bakgrunnsjobb som gjenopptar berikelsen av inntektsmeldingen senere dersom oppslaget mot pdl feiler`() {
        val tidligereInntektsmelding = mockInntektsmeldingGammeltFormat()

        val innsendingId = imRepository.lagreInntektsmeldingSkjema(Mock.skjema, 10.desember.atStartOfDay())
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
            Key.KONTEKST_ID to Mock.transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.ARBEIDSGIVER_FNR to Mock.fnrAg.toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to Mock.skjema.toJson(SkjemaInntektsmelding.serializer()),
                    Key.INNSENDING_ID to innsendingId.toJson(Long.serializer()),
                    Key.MOTTATT to Mock.mottatt.toJson(),
                ).toJson(),
        )

        // Forespørsel hentet
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.FORESPOERSEL_SVAR)
            .firstAsMap()
            .verifiserTransaksjonId(Mock.transaksjonId)
            .verifiserForespoerselIdFraSkjema()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.FORESPOERSEL_SVAR]?.fromJson(Forespoersel.serializer()) shouldBe Mock.forespoersel
            }

        // Virksomhetsnavn hentet
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.VIRKSOMHETER)
            .firstAsMap()
            .verifiserTransaksjonId(Mock.transaksjonId)
            .verifiserForespoerselIdFraSkjema()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.VIRKSOMHETER]?.fromJson(orgMapSerializer) shouldBe mapOf(Mock.forespoersel.orgnr to "Bedrift A/S")
            }

        // Personnavn ikke hentet
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.PERSONER)
            .all()
            .size shouldBe 0

        // Feilmelding
        messages.filterFeil().all().size shouldBe 1

        // Det blir satt opp en bakgrunnsjobb som kan gjenoppta berikelsen senere
        val bakgrunnsjobb = bakgrunnsjobbRepository.getById(Mock.transaksjonId)

        bakgrunnsjobb.also {
            it.shouldNotBeNull()
            it.data.parseJson().also { data ->
                data.lesBehov() shouldBe BehovType.HENT_PERSONER
                data.toMap().verifiserForespoerselId().verifiserTransaksjonId(Mock.transaksjonId)
            }
        }

        // Gjenoppta berikelsen ved å kjøre den utløsende meldingen i bakgrunnsjobben
        bakgrunnsjobb?.data?.let { publish(it) }

        // Sykmeldt og innsender hentet
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.PERSONER)
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
                    it[Key.KONTEKST_ID]
                        .shouldNotBeNull()
                        .fromJson(UuidSerializer)

                    val data = it[Key.DATA].shouldNotBeNull().toMap()

                    data[Key.FORESPOERSEL_ID]
                        .shouldNotBeNull()
                        .fromJson(UuidSerializer)

                    data[Key.INNTEKTSMELDING]
                        .shouldNotBeNull()
                        .fromJson(Inntektsmelding.serializer())
                }
            }
    }

    private fun Map<Key, JsonElement>.verifiserTransaksjonId(transaksjonId: UUID): Map<Key, JsonElement> =
        also {
            Key.KONTEKST_ID.lesOrNull(UuidSerializer, it) shouldBe transaksjonId
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
        val mottatt = 19.august.kl(19, 5, 0, 0)

        val skjema = mockSkjemaInntektsmelding()

        val forespoerselId: UUID = skjema.forespoerselId

        val forespoersel =
            Forespoersel(
                orgnr = orgnr,
                fnr = bjarneBetjent.ident!!.let(::Fnr),
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
                bestemmendeFravaersdager = mapOf(orgnr to 15.juli),
                forespurtData = mockForespurtData(),
                erBesvart = false,
                opprettetUpresisIkkeBruk = 2.august,
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
                opprettetUpresisIkkeBruk = forespoersel.opprettetUpresisIkkeBruk,
            )
    }
}
