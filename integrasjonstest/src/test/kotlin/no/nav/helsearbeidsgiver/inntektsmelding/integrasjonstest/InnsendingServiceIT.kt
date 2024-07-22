package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.assertions.throwables.shouldNotThrowAny
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
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.mock.gyldigInnsendingRequest
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.toForespoersel
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
        forespoerselRepository.lagreForespoersel(Mock.forespoerselId.toString(), Mock.orgnr.verdi)
        forespoerselRepository.oppdaterSakId(Mock.forespoerselId.toString(), Mock.SAK_ID)
        forespoerselRepository.oppdaterOppgaveId(Mock.forespoerselId.toString(), Mock.OPPGAVE_ID)

        val transaksjonId: UUID = UUID.randomUUID()

        mockForespoerselSvarFraHelsebro(
            eventName = EventName.INSENDING_STARTED,
            transaksjonId = transaksjonId,
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
            Key.DATA to "".toJson(),
            Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
            Key.ORGNRUNDERENHET to Mock.orgnr.toJson(),
            Key.IDENTITETSNUMMER to Mock.fnr.toJson(),
            Key.ARBEIDSGIVER_ID to Mock.fnrAg.toJson(),
            Key.SKJEMA_INNTEKTSMELDING to gyldigInnsendingRequest.toJson(Innsending.serializer()),
        )

        // Forespørsel hentet
        messages
            .filter(EventName.INSENDING_STARTED)
            .filter(Key.FORESPOERSEL_SVAR)
            .firstAsMap()
            .verifiserTransaksjonId(transaksjonId)
            .verifiserForespoerselId()
            .also {
                it shouldContainKey Key.DATA
                it[Key.FORESPOERSEL_SVAR]?.fromJson(Forespoersel.serializer()) shouldBe Mock.forespoersel
            }

        // Virksomhetsnavn hentet
        messages
            .filter(EventName.INSENDING_STARTED)
            .filter(Key.VIRKSOMHET)
            .firstAsMap()
            .verifiserTransaksjonId(transaksjonId)
            .verifiserForespoerselId()
            .also {
                it shouldContainKey Key.DATA
                it[Key.VIRKSOMHET]?.fromJson(String.serializer()) shouldBe "Bedrift A/S"
            }

        // Sykmeldt og innsender hentet
        messages
            .filter(EventName.INSENDING_STARTED)
            .filter(Key.ARBEIDSTAKER_INFORMASJON)
            .filter(Key.ARBEIDSGIVER_INFORMASJON)
            .firstAsMap()
            .verifiserTransaksjonId(transaksjonId)
            .verifiserForespoerselId()
            .also {
                it shouldContainKey Key.DATA

                shouldNotThrowAny {
                    it[Key.ARBEIDSTAKER_INFORMASJON]
                        .shouldNotBeNull()
                        .fromJson(PersonDato.serializer())

                    it[Key.ARBEIDSGIVER_INFORMASJON]
                        .shouldNotBeNull()
                        .fromJson(PersonDato.serializer())
                }
            }

        // Inntektsmelding lagret
        messages
            .filter(EventName.INSENDING_STARTED)
            .filter(Key.INNTEKTSMELDING_DOKUMENT)
            .filter(Key.ER_DUPLIKAT_IM)
            .firstAsMap()
            .verifiserTransaksjonId(transaksjonId)
            .verifiserForespoerselId()
            .also {
                it shouldContainKey Key.DATA

                shouldNotThrowAny {
                    it[Key.INNTEKTSMELDING_DOKUMENT]
                        .shouldNotBeNull()
                        .fromJson(Inntektsmelding.serializer())

                    it[Key.ER_DUPLIKAT_IM]
                        .shouldNotBeNull()
                        .fromJson(Boolean.serializer())
                }
            }

        // Siste melding fra service
        messages
            .filter(EventName.INNTEKTSMELDING_MOTTATT)
            .firstAsMap()
            .verifiserTransaksjonId(transaksjonId)
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

        // API besvart gjennom redis
        shouldNotThrowAny {
            redisConnection
                .get(transaksjonId)
                .shouldNotBeNull()
                .fromJson(ResultJson.serializer())
                .success
                .shouldNotBeNull()
                .fromJson(Inntektsmelding.serializer())
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
        const val SAK_ID = "tjukk-kalender"
        const val OPPGAVE_ID = "kunstig-demon"

        val orgnr = Orgnr.genererGyldig()
        val fnr = Fnr.genererGyldig()
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

        val forespoersel = forespoerselSvar.toForespoersel()
    }
}
