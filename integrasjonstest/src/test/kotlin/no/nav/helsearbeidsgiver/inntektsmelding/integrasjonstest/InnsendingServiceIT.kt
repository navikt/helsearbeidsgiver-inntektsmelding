package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.dokarkiv.domene.OpprettOgFerdigstillResponse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.test.mock.GYLDIG_INNSENDING_REQUEST
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InnsendingServiceIT : EndToEndTest() {

    @Test
    fun `Test at innsending er mottatt`() {
        forespoerselRepository.lagreForespoersel(Mock.forespoerselId.toString(), Mock.ORGNR)
        forespoerselRepository.oppdaterSakId(Mock.forespoerselId.toString(), Mock.SAK_ID)
        forespoerselRepository.oppdaterOppgaveId(Mock.forespoerselId.toString(), Mock.OPPGAVE_ID)

        coEvery {
            dokarkivClient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
        } returns OpprettOgFerdigstillResponse(
            journalpostId = "journalpost-id-sukkerspinn",
            journalpostFerdigstilt = true,
            melding = "Ha en brillefin dag!",
            dokumenter = emptyList()
        )

        publish(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.CLIENT_ID to Mock.clientId.toJson(),
            Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
            Key.ORGNRUNDERENHET to Mock.ORGNR.toJson(),
            Key.IDENTITETSNUMMER to Mock.FNR.toJson(),
            Key.ARBEIDSGIVER_ID to Mock.FNR_AG.toJson(),
            Key.INNTEKTSMELDING to GYLDIG_INNSENDING_REQUEST.toJson(Innsending.serializer())
        )

        Thread.sleep(10000)

        // Alle transaksjonId skal være like. Finn første og beste som sammenligningsgrunnlag.
        val transaksjonId = messages.all()
            .firstNotNullOf {
                it.toMap()[Key.UUID]
            }
            .fromJson(UuidSerializer)

        // Data hentet
        messages.filter(EventName.INSENDING_STARTED)
            .filter(Key.VIRKSOMHET)
            .firstAsMap()
            .verifiserTransaksjonId(transaksjonId)
            .verifiserForespoerselId()
            .also {
                it shouldContainKey Key.DATA
                it[Key.VIRKSOMHET]?.fromJson(String.serializer()) shouldBe "Bedrift A/S"
            }

        // Data hentet
        messages.filter(EventName.INSENDING_STARTED)
            .filter(Key.ARBEIDSFORHOLD)
            .firstAsMap()
            .verifiserTransaksjonId(transaksjonId)
            .verifiserForespoerselId()
            .also {
                it shouldContainKey Key.DATA
                it[Key.ARBEIDSFORHOLD].shouldNotBeNull()
            }

        // Data hentet
        messages.filter(EventName.INSENDING_STARTED)
            .filter(Key.ARBEIDSTAKER_INFORMASJON)
            .filter(Key.ARBEIDSGIVER_INFORMASJON)
            .firstAsMap()
            .verifiserTransaksjonId(transaksjonId)
            .verifiserForespoerselId()
            .also {
                it shouldContainKey Key.DATA

                shouldNotThrowAny {
                    it[Key.ARBEIDSTAKER_INFORMASJON].shouldNotBeNull()
                        .fromJson(PersonDato.serializer())

                    it[Key.ARBEIDSGIVER_INFORMASJON].shouldNotBeNull()
                        .fromJson(PersonDato.serializer())
                }
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
                        .fromJson(Inntektsmelding.serializer())

                    it[Key.ER_DUPLIKAT_IM].shouldNotBeNull()
                        .fromJson(Boolean.serializer())
                }
            }

        // Siste melding fra service
        messages.filter(EventName.INNTEKTSMELDING_MOTTATT)
            .firstAsMap()
            .verifiserTransaksjonId(transaksjonId)
            .verifiserForespoerselId()
            .also {
                shouldNotThrowAny {
                    it[Key.UUID].shouldNotBeNull()
                        .fromJson(UuidSerializer)

                    it[Key.FORESPOERSEL_ID].shouldNotBeNull()
                        .fromJson(UuidSerializer)

                    it[Key.INNTEKTSMELDING_DOKUMENT].shouldNotBeNull()
                        .fromJson(Inntektsmelding.serializer())
                }
            }

        // Ingen feil
        messages.filterFeil().all().size shouldBe 0

        // API besvart gjennom redis
        shouldNotThrowAny {
            redisStore.get(RedisKey.of(Mock.clientId))
                .shouldNotBeNull()
                .fromJson(Inntektsmelding.serializer())
        }
    }

    private fun Map<IKey, JsonElement>.verifiserTransaksjonId(transaksjonId: UUID): Map<IKey, JsonElement> =
        also {
            Key.UUID.lesOrNull(UuidSerializer, it) shouldBe transaksjonId
        }

    private fun Map<IKey, JsonElement>.verifiserForespoerselId(): Map<IKey, JsonElement> =
        also {
            Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, it) shouldBe Mock.forespoerselId
        }

    private object Mock {
        const val ORGNR = "stolt-krakk"
        const val FNR = "kongelig-albatross"
        const val FNR_AG = "uutgrunnelig-koffert"
        const val SAK_ID = "tjukk-kalender"
        const val OPPGAVE_ID = "kunstig-demon"

        val clientId = randomUuid()
        val forespoerselId = randomUuid()
    }
}
