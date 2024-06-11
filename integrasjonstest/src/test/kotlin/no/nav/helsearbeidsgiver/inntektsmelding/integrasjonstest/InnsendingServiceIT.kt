package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
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
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.test.mock.GYLDIG_INNSENDING_REQUEST
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.toForespoersel
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
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
        forespoerselRepository.lagreForespoersel(Mock.forespoerselId.toString(), Mock.ORGNR.toString())
        forespoerselRepository.oppdaterSakId(Mock.forespoerselId.toString(), Mock.SAK_ID)
        forespoerselRepository.oppdaterOppgaveId(Mock.forespoerselId.toString(), Mock.OPPGAVE_ID)

        val transaksjonId: UUID = randomUuid()

        mockForespoerselSvarFraHelsebro(
            eventName = EventName.INSENDING_STARTED,
            transaksjonId = transaksjonId,
            forespoerselId = Mock.forespoerselId,
            forespoerselSvar = Mock.forespoerselSvar
        )

        coEvery {
            dokarkivClient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
        } returns OpprettOgFerdigstillResponse(
            journalpostId = "journalpost-id-sukkerspinn",
            journalpostFerdigstilt = true,
            melding = "Ha en brillefin dag!",
            dokumenter = emptyList()
        )

        mockStatic(::randomUuid) {
            every { randomUuid() } returns transaksjonId
            publish(
                Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
                Key.CLIENT_ID to Mock.clientId.toJson(),
                Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                Key.ORGNRUNDERENHET to Mock.ORGNR.toString().toJson(),
                Key.IDENTITETSNUMMER to Mock.FNR.toString().toJson(),
                Key.ARBEIDSGIVER_ID to Mock.FNR_AG.toString().toJson(),
                Key.SKJEMA_INNTEKTSMELDING to GYLDIG_INNSENDING_REQUEST.toJson(Innsending.serializer())
            )
        }

        // Inntektsmelding lagret
        messages.filter(EventName.INSENDING_STARTED)
            .filter(Key.INNTEKTSMELDING_DOKUMENT)
            .filter(Key.ER_DUPLIKAT_IM)
            .firstAsMap()
            .verifiserTransaksjonId(transaksjonId)
            .verifiserForespoerselId()
            .also {
                it shouldContainKey Key.DATA

                shouldNotThrowAny {
                    it[Key.INNTEKTSMELDING_DOKUMENT].shouldNotBeNull()
                        .fromJson(Innsending.serializer())

                    it[Key.ER_DUPLIKAT_IM].shouldNotBeNull()
                        .fromJson(Boolean.serializer())
                }
            }

        // Siste melding fra service
        messages.filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .firstAsMap()
            .verifiserTransaksjonId(transaksjonId)
            .verifiserForespoerselId()
            .also {
                shouldNotThrowAny {
                    it[Key.UUID].shouldNotBeNull()
                        .fromJson(UuidSerializer)

                    it[Key.FORESPOERSEL_ID].shouldNotBeNull()
                        .fromJson(UuidSerializer)

                    it[Key.ORGNRUNDERENHET].shouldNotBeNull()
                        .fromJson(Orgnr.serializer())

                    it[Key.IDENTITETSNUMMER].shouldNotBeNull()
                        .fromJson(Fnr.serializer())

                    it[Key.ARBEIDSGIVER_ID].shouldNotBeNull()
                        .fromJson(Fnr.serializer())

                    it[Key.SKJEMA_INNTEKTSMELDING].shouldNotBeNull()
                        .fromJson(Innsending.serializer())
                }
            }

        // Ingen feil
        messages.filterFeil().all().size shouldBe 0

        // API besvart gjennom redis
        shouldNotThrowAny {
            redisStore.get(RedisKey.of(Mock.clientId))
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
            Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, it) shouldBe Mock.forespoerselId
        }

    private object Mock {
        val ORGNR = Orgnr.genererGyldig()
        val FNR = Fnr.genererGyldig()
        val FNR_AG = Fnr.genererGyldig()
        const val SAK_ID = "tjukk-kalender"
        const val OPPGAVE_ID = "kunstig-demon"

        val clientId: UUID = UUID.randomUUID()
        val forespoerselId: UUID = UUID.randomUUID()
        val vedtaksperiodeId: UUID = UUID.randomUUID()

        val forespoerselSvar = ForespoerselSvar.Suksess(
            type = ForespoerselType.KOMPLETT,
            orgnr = GYLDIG_INNSENDING_REQUEST.orgnrUnderenhet,
            fnr = GYLDIG_INNSENDING_REQUEST.identitetsnummer,
            vedtaksperiodeId = vedtaksperiodeId,
            egenmeldingsperioder = GYLDIG_INNSENDING_REQUEST.egenmeldingsperioder,
            sykmeldingsperioder = GYLDIG_INNSENDING_REQUEST.fraværsperioder,
            skjaeringstidspunkt = GYLDIG_INNSENDING_REQUEST.bestemmendeFraværsdag,
            bestemmendeFravaersdager = GYLDIG_INNSENDING_REQUEST.fraværsperioder.lastOrNull()
                ?.let { mapOf(GYLDIG_INNSENDING_REQUEST.orgnrUnderenhet to it.fom) }
                .orEmpty(),
            forespurtData = mockForespurtData(),
            erBesvart = false
        )

        val forespoersel = forespoerselSvar.toForespoersel()
    }
}
