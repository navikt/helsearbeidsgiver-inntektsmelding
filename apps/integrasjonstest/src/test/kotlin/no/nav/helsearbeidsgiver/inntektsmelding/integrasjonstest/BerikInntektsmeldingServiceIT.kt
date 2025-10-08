package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.kontrakt.domene.forespoersel.test.mockForespurtData
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.lesOrNull
import no.nav.hag.simba.utils.felles.json.orgMapSerializer
import no.nav.hag.simba.utils.felles.json.personMapSerializer
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.test.json.lesBehov
import no.nav.hag.simba.utils.felles.test.mock.mockInntektsmeldingV1
import no.nav.hag.simba.utils.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.helsearbeidsgiver.dokarkiv.domene.OpprettOgFerdigstillResponse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
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
        val tidligereInntektsmelding = mockInntektsmeldingV1()

        imRepository.lagreInntektsmeldingSkjema(tidligereInntektsmelding.id, Mock.skjema, Fnr.genererGyldig(), 10.desember.atStartOfDay())
        imRepository.oppdaterMedInntektsmelding(tidligereInntektsmelding)

        coEvery {
            dokarkivClient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any())
        } returns
            OpprettOgFerdigstillResponse(
                journalpostId = "journalpost-id-sukkerspinn",
                journalpostFerdigstilt = true,
                melding = "Ha en brillefin dag!",
                dokumenter = emptyList(),
            )

        publish(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_SKJEMA_LAGRET.toJson(),
            Key.KONTEKST_ID to Mock.kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.ARBEIDSGIVER_FNR to Mock.fnrAg.toJson(),
                    Key.FORESPOERSEL_SVAR to Mock.forespoersel.toJson(Forespoersel.serializer()),
                    Key.INNTEKTSMELDING_ID to UUID.randomUUID().toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to Mock.skjema.toJson(SkjemaInntektsmelding.serializer()),
                    Key.MOTTATT to Mock.mottatt.toJson(),
                ).toJson(),
        )

        // Virksomhetsnavn hentet
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.VIRKSOMHETER)
            .firstAsMap()
            .verifiserKontekstId(Mock.kontekstId)
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
            .verifiserKontekstId(Mock.kontekstId)
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
            .verifiserKontekstId(Mock.kontekstId)
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
            .verifiserKontekstId(Mock.kontekstId)
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
        val tidligereInntektsmelding = mockInntektsmeldingV1()

        imRepository.lagreInntektsmeldingSkjema(tidligereInntektsmelding.id, Mock.skjema, Fnr.genererGyldig(), 10.desember.atStartOfDay())
        imRepository.oppdaterMedInntektsmelding(tidligereInntektsmelding)

        coEvery {
            dokarkivClient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any())
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

        publish(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_SKJEMA_LAGRET.toJson(),
            Key.KONTEKST_ID to Mock.kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.ARBEIDSGIVER_FNR to Mock.fnrAg.toJson(),
                    Key.FORESPOERSEL_SVAR to Mock.forespoersel.toJson(Forespoersel.serializer()),
                    Key.INNTEKTSMELDING_ID to UUID.randomUUID().toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to Mock.skjema.toJson(SkjemaInntektsmelding.serializer()),
                    Key.MOTTATT to Mock.mottatt.toJson(),
                ).toJson(),
        )

        // Virksomhetsnavn hentet
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.VIRKSOMHETER)
            .firstAsMap()
            .verifiserKontekstId(Mock.kontekstId)
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
        val bakgrunnsjobb = bakgrunnsjobbRepository.getById(Mock.kontekstId)

        bakgrunnsjobb.also {
            it.shouldNotBeNull()
            it.data.parseJson().also { data ->
                data.lesBehov() shouldBe BehovType.HENT_PERSONER
                data.toMap().verifiserForespoerselIdFraSkjema().verifiserKontekstId(Mock.kontekstId)
            }
        }

        // Gjenoppta berikelsen ved å kjøre den utløsende meldingen i bakgrunnsjobben
        bakgrunnsjobb?.data?.let {
            publish(
                *it
                    .parseJson()
                    .toMap()
                    .toList()
                    .toTypedArray(),
            )
        }

        // Sykmeldt og innsender hentet
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.PERSONER)
            .firstAsMap()
            .verifiserKontekstId(Mock.kontekstId)
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
            .verifiserKontekstId(Mock.kontekstId)
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

    private fun Map<Key, JsonElement>.verifiserKontekstId(kontekstId: UUID): Map<Key, JsonElement> =
        also {
            Key.KONTEKST_ID.lesOrNull(UuidSerializer, it) shouldBe kontekstId
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
        val kontekstId: UUID = UUID.randomUUID()
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
                erBegrenset = false,
            )
    }
}
