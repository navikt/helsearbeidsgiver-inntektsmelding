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
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Forespoersel
import no.nav.helsearbeidsgiver.felles.ForespoerselType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.mock.gyldigInnsendingRequest
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
import org.junit.Ignore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BerikInntektsmeldingServiceIT : EndToEndTest() {
    @Ignore
    @Test
    fun `skal berike og lagre inntektsmeldinger`() {
        forespoerselRepository.lagreForespoersel(Mock.forespoerselId.toString(), Mock.orgnr.toString())
        forespoerselRepository.oppdaterSakId(Mock.forespoerselId.toString(), Mock.SAK_ID)
        forespoerselRepository.oppdaterOppgaveId(Mock.forespoerselId.toString(), Mock.OPPGAVE_ID)

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
            eventName = EventName.INNTEKTSMELDING_SKJEMA_LAGRET,
            transaksjonId = Mock.transaksjonId,
            forespoerselId = Mock.forespoerselId,
            forespoerselSvar = Mock.forespoerselSvar,
        )

        mockStatic(::randomUuid) {
            every { randomUuid() } returns Mock.transaksjonId

            publish(
                Key.EVENT_NAME to EventName.INNTEKTSMELDING_SKJEMA_LAGRET.toJson(),
                Key.UUID to Mock.transaksjonId.toJson(),
                Key.DATA to "".toJson(),
                Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                Key.ORGNRUNDERENHET to Mock.orgnr.toJson(Orgnr.serializer()),
                Key.IDENTITETSNUMMER to Mock.fnr.toJson(Fnr.serializer()),
                Key.ARBEIDSGIVER_ID to Mock.fnr.toJson(Fnr.serializer()),
                Key.SKJEMA_INNTEKTSMELDING to gyldigInnsendingRequest.toJson(Innsending.serializer()),
            )
        }

        // Forespørsel hentet
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.FORESPOERSEL_SVAR)
            .firstAsMap()
            .verifiserTransaksjonId(Mock.transaksjonId)
            .verifiserForespoerselId()
            .also {
                it shouldContainKey Key.DATA
                it[Key.FORESPOERSEL_SVAR]?.fromJson(Forespoersel.serializer()) shouldBe Mock.forespoersel
            }

        // Virksomhetsnavn hentet
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.VIRKSOMHET)
            .firstAsMap()
            .verifiserTransaksjonId(Mock.transaksjonId)
            .verifiserForespoerselId()
            .also {
                it shouldContainKey Key.DATA
                it[Key.VIRKSOMHET]?.fromJson(String.serializer()) shouldBe "Bedrift A/S"
            }

        // Sykmeldt og innsender hentet
        messages
            .filter(EventName.INNTEKTSMELDING_SKJEMA_LAGRET)
            .filter(Key.ARBEIDSTAKER_INFORMASJON)
            .filter(Key.ARBEIDSGIVER_INFORMASJON)
            .firstAsMap()
            .verifiserTransaksjonId(Mock.transaksjonId)
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

    private fun Map<Key, JsonElement>.verifiserTransaksjonId(transaksjonId: UUID): Map<Key, JsonElement> =
        also {
            Key.UUID.lesOrNull(UuidSerializer, it) shouldBe transaksjonId
        }

    private fun Map<Key, JsonElement>.verifiserForespoerselId(): Map<Key, JsonElement> =
        also {
            Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, it) shouldBe Mock.forespoerselId
        }

    private object Mock {
        val fnr = Fnr.genererGyldig()
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
