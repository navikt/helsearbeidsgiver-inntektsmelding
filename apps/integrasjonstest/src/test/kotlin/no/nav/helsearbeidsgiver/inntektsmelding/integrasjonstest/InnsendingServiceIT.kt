package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.dokarkiv.domene.OpprettOgFerdigstillResponse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.ForespoerselFraBro
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.desember
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.kl
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import no.nav.helsearbeidsgiver.utils.test.date.september
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InnsendingServiceIT : EndToEndTest() {
    @Test
    fun `Test at innsending er mottatt`() {
        val kontekstId: UUID = UUID.randomUUID()
        val tidligereInntektsmelding = mockInntektsmeldingV1()

        imRepository.lagreInntektsmeldingSkjema(tidligereInntektsmelding.id, Mock.skjema, 9.desember.atStartOfDay())
        imRepository.oppdaterMedBeriketDokument(tidligereInntektsmelding)

        mockForespoerselSvarFraHelsebro(
            forespoerselId = Mock.skjema.forespoerselId,
            forespoerselSvar = Mock.forespoerselSvar,
        )

        coEvery {
            dokarkivClient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
        } returns
            OpprettOgFerdigstillResponse(
                journalpostId = "journalpost-id-sukkerspinn",
                journalpostFerdigstilt = true,
                melding = "Ha en brillefin dag!",
                dokumenter = emptyList(),
            )

        val nyInnsending =
            Mock.skjema.let {
                it.copy(
                    agp =
                        it.agp?.copy(
                            egenmeldinger =
                                listOf(
                                    6.oktober til 11.oktober,
                                ),
                        ),
                )
            }

        publish(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to Mock.skjema.forespoerselId.toJson(),
                    Key.ARBEIDSGIVER_FNR to Mock.fnrAg.toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to nyInnsending.toJson(SkjemaInntektsmelding.serializer()),
                    Key.MOTTATT to Mock.mottatt.toJson(),
                ).toJson(),
        )

        // Forespørsel hentet
        messages
            .filter(EventName.INSENDING_STARTED)
            .filter(Key.FORESPOERSEL_SVAR)
            .firstAsMap()
            .verifiserKontekstId(kontekstId)
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.FORESPOERSEL_SVAR]?.fromJson(Forespoersel.serializer()) shouldBe Mock.forespoerselSvar.toForespoersel()
            }

        // Inntektsmelding lagret
        messages
            .filter(EventName.INSENDING_STARTED)
            .filter(Key.ER_DUPLIKAT_IM)
            .firstAsMap()
            .verifiserKontekstId(kontekstId)
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.ER_DUPLIKAT_IM]?.fromJson(Boolean.serializer()) shouldBe false
            }

        // Siste melding fra service
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .firstAsMap()
            .verifiserKontekstId(kontekstId)
            .verifiserForespoerselIdFraSkjema()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()

                shouldNotThrowAny {
                    it[Key.KONTEKST_ID]
                        .shouldNotBeNull()
                        .fromJson(UuidSerializer)

                    data[Key.ARBEIDSGIVER_FNR]
                        .shouldNotBeNull()
                        .fromJson(Fnr.serializer())

                    data[Key.FORESPOERSEL_SVAR]
                        .shouldNotBeNull()
                        .fromJson(Forespoersel.serializer())

                    data[Key.INNTEKTSMELDING_ID]
                        .shouldNotBeNull()
                        .fromJson(UuidSerializer)

                    data[Key.SKJEMA_INNTEKTSMELDING]
                        .shouldNotBeNull()
                        .fromJson(SkjemaInntektsmelding.serializer())

                    data[Key.INNSENDING_ID]
                        .shouldNotBeNull()
                        .fromJson(Long.serializer())
                }
            }

        // Ingen feil
        messages.filterFeil().all().size shouldBe 0

        // API besvart gjennom redis
        shouldNotThrowAny {
            redisConnection
                .get(RedisPrefix.Innsending, kontekstId)
                .shouldNotBeNull()
                .fromJson(ResultJson.serializer())
                .success
                .shouldNotBeNull()
                .fromJson(UuidSerializer)
        }
    }

    private fun Map<Key, JsonElement>.verifiserKontekstId(kontekstId: UUID): Map<Key, JsonElement> =
        also {
            Key.KONTEKST_ID.lesOrNull(UuidSerializer, it) shouldBe kontekstId
        }

    private fun Map<Key, JsonElement>.verifiserForespoerselIdFraSkjema(): Map<Key, JsonElement> =
        also {
            val data = it[Key.DATA]?.toMap().orEmpty()
            val skjema = Key.SKJEMA_INNTEKTSMELDING.lesOrNull(SkjemaInntektsmelding.serializer(), data)
            skjema?.forespoerselId shouldBe Mock.skjema.forespoerselId
        }

    private object Mock {
        val skjema = mockSkjemaInntektsmelding()

        val orgnr = Orgnr.genererGyldig()
        val fnrAg = Fnr.genererGyldig()
        val vedtaksperiodeId: UUID = UUID.randomUUID()
        val mottatt = 6.september.kl(22, 18, 0, 0)

        val forespoerselSvar =
            ForespoerselFraBro(
                orgnr = orgnr,
                fnr = Fnr.genererGyldig(),
                forespoerselId = skjema.forespoerselId,
                vedtaksperiodeId = vedtaksperiodeId,
                sykmeldingsperioder =
                    listOf(
                        3.mars til 13.mars,
                        17.mars til 5.april,
                    ),
                egenmeldingsperioder =
                    listOf(
                        24.februar til 26.februar,
                        1.mars til 1.mars,
                    ),
                bestemmendeFravaersdager = mapOf(orgnr to 17.mars),
                forespurtData = mockForespurtData(),
                erBesvart = false,
                opprettetUpresisIkkeBruk = 19.mars,
            )
    }
}
