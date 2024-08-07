package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.dokarkiv.domene.OpprettOgFerdigstillResponse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.ForespoerselType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.test.mock.gyldigInnsendingRequest
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
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
        val transaksjonId: UUID = UUID.randomUUID()
        val tidligereInntektsmelding = mockInntektsmelding()

        forespoerselRepository.lagreForespoersel(Mock.forespoerselId.toString(), Mock.orgnr.verdi)
        forespoerselRepository.oppdaterSakId(Mock.forespoerselId.toString(), Mock.SAK_ID)
        forespoerselRepository.oppdaterOppgaveId(Mock.forespoerselId.toString(), Mock.OPPGAVE_ID)
        imRepository.lagreInntektsmelding(Mock.forespoerselId, tidligereInntektsmelding)

        mockForespoerselSvarFraHelsebro(
            forespoerselId = Mock.forespoerselId,
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

        publish(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                    Key.ARBEIDSGIVER_FNR to Mock.fnrAg.toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to gyldigInnsendingRequest.toJson(Innsending.serializer()),
                ).toJson(),
        )

        // Inntektsmelding lagret
        messages
            .filter(EventName.INSENDING_STARTED)
            .filter(Key.ER_DUPLIKAT_IM, nestedData = true)
            .firstAsMap()
            .verifiserTransaksjonId(transaksjonId)
            .verifiserForespoerselId()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.ER_DUPLIKAT_IM]?.fromJson(Boolean.serializer()) shouldBe false
            }

        // Siste melding fra service
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .firstAsMap()
            .verifiserTransaksjonId(transaksjonId)
            .verifiserForespoerselId()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()

                shouldNotThrowAny {
                    it[Key.UUID]
                        .shouldNotBeNull()
                        .fromJson(UuidSerializer)

                    data[Key.FORESPOERSEL_ID]
                        .shouldNotBeNull()
                        .fromJson(UuidSerializer)

                    data[Key.ARBEIDSGIVER_FNR]
                        .shouldNotBeNull()
                        .fromJson(Fnr.serializer())

                    data[Key.SKJEMA_INNTEKTSMELDING]
                        .shouldNotBeNull()
                        .fromJson(Innsending.serializer())
                }
            }

        // Ingen feil
        messages.filterFeil().all().size shouldBe 0

        // API besvart gjennom redis
        shouldNotThrowAny {
            redisConnection
                .get(RedisPrefix.Innsending, transaksjonId)
                .shouldNotBeNull()
                .fromJson(ResultJson.serializer())
                .success
                .shouldNotBeNull()
                .fromJson(Innsending.serializer())
        }
    }

    private fun Map<Key, JsonElement>.verifiserTransaksjonId(transaksjonId: UUID): Map<Key, JsonElement> =
        also {
            Key.UUID.lesOrNull(UuidSerializer, it) shouldBe transaksjonId
        }

    private fun Map<Key, JsonElement>.verifiserForespoerselId(): Map<Key, JsonElement> =
        also {
            val data = it[Key.DATA]?.toMap().orEmpty()
            val forespoerselId = Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, data)
            forespoerselId shouldBe Mock.forespoerselId
        }

    private object Mock {
        const val SAK_ID = "tjukk-kalender"
        const val OPPGAVE_ID = "kunstig-demon"

        val orgnr = Orgnr.genererGyldig()
        val fnrAg = Fnr.genererGyldig()
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
    }
}
