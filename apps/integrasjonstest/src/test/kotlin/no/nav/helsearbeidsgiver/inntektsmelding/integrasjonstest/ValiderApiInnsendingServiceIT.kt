package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.verify
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Forespoersel
import no.nav.hag.simba.utils.felles.domene.ForespoerselFraBro
import no.nav.hag.simba.utils.felles.json.inntektMapSerializer
import no.nav.hag.simba.utils.felles.json.lesOrNull
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.test.json.lesBehov
import no.nav.hag.simba.utils.felles.test.mock.mockForespurtData
import no.nav.hag.simba.utils.felles.test.mock.mockInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.api.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.kafka.innsendingtopic.Innsending.toJson
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.ekstern.AvvistInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.ekstern.Feilkode
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.ApiInnsendingIT.Companion.medInntekt
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateTimeSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.desember
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.kl
import no.nav.helsearbeidsgiver.utils.test.date.november
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import no.nav.helsearbeidsgiver.felles.kafka.innsendingtopic.Innsending.EventName as InnsendingEventName
import no.nav.helsearbeidsgiver.felles.kafka.innsendingtopic.Innsending.Key as InnsendingKey

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValiderApiInnsendingServiceIT : EndToEndTest() {
    @BeforeEach
    fun setup() {
        truncateDatabase()
    }

    @Test
    fun `skal validere og sende inntektsmelding videre dersom den er OK`() {
        mockForespoerselSvarFraHelsebro(
            forespoerselId = Mock.forespoerselId,
            forespoerselSvar = Mock.forespoerselSvar,
        )

        coEvery {
            inntektClient.hentInntektPerOrgnrOgMaaned(
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns mapOf(Mock.orgnr.toString() to Mock.inntektFraAordningen)
        publish(
            Key.EVENT_NAME to EventName.API_INNSENDING_STARTET.toJson(),
            Key.KONTEKST_ID to Mock.kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.INNSENDING to Mock.innsending.toJson(Innsending.serializer()),
                    Key.MOTTATT to Mock.mottatt.toJson(),
                ).toJson(),
        )

        // Ber om forespørsel
        messages
            .filter(EventName.API_INNSENDING_STARTET)
            .filter(BehovType.HENT_TRENGER_IM)
            .firstAsMap()
            .verifiserKontekstId(Mock.kontekstId)
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
            }

        // Forespørsel hentet
        messages
            .filter(EventName.API_INNSENDING_STARTET)
            .filter(Key.FORESPOERSEL_SVAR)
            .firstAsMap()
            .verifiserKontekstId(Mock.kontekstId)
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.FORESPOERSEL_SVAR]?.fromJson(Forespoersel.serializer()) shouldBe Mock.forespoersel
            }

        // Ber om inntekt
        messages
            .filter(EventName.API_INNSENDING_STARTET)
            .filter(BehovType.HENT_INNTEKT)
            .firstAsMap()
            .verifiserKontekstId(Mock.kontekstId)
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.ORGNR_UNDERENHET]?.fromJson(Orgnr.serializer()) shouldBe Mock.forespoersel.orgnr
                data[Key.FNR]?.fromJson(Fnr.serializer()) shouldBe Mock.forespoersel.fnr
            }

        // Inntekt hentet
        messages
            .filter(EventName.API_INNSENDING_STARTET)
            .filter(Key.INNTEKT)
            .firstAsMap()
            .verifiserKontekstId(Mock.kontekstId)
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.INNTEKT]?.fromJson(inntektMapSerializer) shouldBe Mock.inntektFraAordningen
            }

        // Inntektsmelding validert
        messages
            .filter(EventName.API_INNSENDING_VALIDERT)
            .firstAsMap()
            .verifiserKontekstId(Mock.kontekstId)
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()

                shouldNotThrowAny {
                    data[Key.MOTTATT]
                        .shouldNotBeNull()
                        .fromJson(LocalDateTimeSerializer) shouldBe Mock.mottatt

                    data[Key.INNSENDING]
                        .shouldNotBeNull()
                        .fromJson(Innsending.serializer()) shouldBe Mock.innsending

                    data[Key.FORESPOERSEL_SVAR]
                        .shouldNotBeNull()
                        .fromJson(Forespoersel.serializer()) shouldBe Mock.forespoersel
                }
            }

        // Ingen feil
        messages.filterFeil().all().size shouldBe 0
    }

    @Test
    fun `skal opprette bakgrunnsjobb når inntekt-kall feiler og gjenoppta senere`() {
        mockForespoerselSvarFraHelsebro(
            forespoerselId = Mock.forespoerselId,
            forespoerselSvar = Mock.forespoerselSvar,
        )

        coEvery {
            inntektClient.hentInntektPerOrgnrOgMaaned(
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } throws RuntimeException("Inntekt feilet!") andThen mapOf(Mock.orgnr.toString() to Mock.inntektFraAordningen)

        publish(
            Key.EVENT_NAME to EventName.API_INNSENDING_STARTET.toJson(),
            Key.KONTEKST_ID to Mock.kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.INNSENDING to Mock.innsending.toJson(Innsending.serializer()),
                    Key.MOTTATT to Mock.mottatt.toJson(),
                ).toJson(),
        )

        // Hentet forespørsel
        messages
            .filter(EventName.API_INNSENDING_STARTET)
            .filter(Key.FORESPOERSEL_SVAR)
            .firstAsMap()
            .verifiserKontekstId(Mock.kontekstId)
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.FORESPOERSEL_SVAR]?.fromJson(Forespoersel.serializer()) shouldBe Mock.forespoersel
            }

        // Inntektkall feiler
        messages
            .filter(EventName.API_INNSENDING_STARTET)
            .filter(Key.INNTEKT)
            .all()
            .size shouldBe 0

        // Feilmelding oppstår
        messages.filterFeil().all().size shouldBe 1

        // Bakgrunnsjobb opprettes
        val bakgrunnsjobb = bakgrunnsjobbRepository.getById(Mock.kontekstId)

        bakgrunnsjobb.also {
            it.shouldNotBeNull()
            it.data.parseJson().also { data ->
                data.lesBehov() shouldBe BehovType.HENT_INNTEKT
                data.toMap().verifiserKontekstId(Mock.kontekstId)
            }
        }

        // Gjenoppta ved å kjøre bakgrunnsjobb
        bakgrunnsjobb?.data?.let {
            publish(
                *it
                    .parseJson()
                    .toMap()
                    .toList()
                    .toTypedArray(),
            )
        }

        // Nå hentes inntekt
        messages
            .filter(EventName.API_INNSENDING_STARTET)
            .filter(Key.INNTEKT)
            .firstAsMap()
            .verifiserKontekstId(Mock.kontekstId)
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.INNTEKT]?.fromJson(inntektMapSerializer) shouldBe Mock.inntektFraAordningen
            }

        // Validering fullført OK etter å ha kjørt bakgrunnsjobb
        messages
            .filter(EventName.API_INNSENDING_VALIDERT)
            .firstAsMap()
            .verifiserKontekstId(Mock.kontekstId)
    }

    @Test
    fun `skal avvise inntektsmeldingen dersom valideringen mot a-ordningen ikke er OK`() {
        mockForespoerselSvarFraHelsebro(
            forespoerselId = Mock.forespoerselId,
            forespoerselSvar = Mock.forespoerselSvar,
        )

        val inntektFraAordningen =
            mapOf(
                oktober(2017) to Mock.inntektBeloep,
                november(2017) to Mock.inntektBeloep.minus(100),
                desember(2017) to Mock.inntektBeloep.minus(100),
            )

        val kontekstId = UUID.randomUUID()

        coEvery {
            inntektClient.hentInntektPerOrgnrOgMaaned(
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns mapOf(Mock.orgnr.toString() to inntektFraAordningen)

        publish(
            Key.EVENT_NAME to EventName.API_INNSENDING_STARTET.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.INNSENDING to Mock.innsending.toJson(Innsending.serializer()),
                    Key.MOTTATT to Mock.mottatt.toJson(),
                ).toJson(),
        )

        val avvistInntektsmelding =
            AvvistInntektsmelding(
                inntektsmeldingId = Mock.innsending.innsendingId,
                feilkode = Feilkode.INNTEKT_AVVIKER_FRA_A_ORDNINGEN,
            )

        verify(exactly = 1) {
            producer.send(
                key = Mock.forespoerselId,
                message =
                    mapOf(
                        InnsendingKey.EVENT_NAME to InnsendingEventName.AVVIST_INNTEKTSMELDING.toJson(),
                        InnsendingKey.KONTEKST_ID to kontekstId.toJson(),
                        InnsendingKey.DATA to
                            mapOf(
                                InnsendingKey.AVVIST_INNTEKTSMELDING to avvistInntektsmelding.toJson(AvvistInntektsmelding.serializer()),
                            ).toJson(),
                    ),
            )
        }

        // Sender _ikke_ inntektsmeldingen videre i innsendingsløypa
        messages.filter(EventName.API_INNSENDING_VALIDERT).all() shouldBe emptyList()
    }

    private fun Map<Key, JsonElement>.verifiserKontekstId(kontekstId: UUID): Map<Key, JsonElement> =
        also {
            Key.KONTEKST_ID.lesOrNull(UuidSerializer, it) shouldBe kontekstId
        }

    private object Mock {
        val kontekstId: UUID = UUID.randomUUID()
        val mottatt = 19.august.kl(19, 5, 0, 0)

        val orgnr = Orgnr.genererGyldig()

        val inntektBeloep = 544.6
        val inntektsDato = 1.januar
        val inntekt = Inntekt(beloep = inntektBeloep, inntektsdato = inntektsDato, naturalytelser = emptyList(), endringAarsaker = emptyList())
        val innsending = mockInnsending().medInntekt(inntekt)
        val forespoerselId = innsending.skjema.forespoerselId

        val inntektFraAordningen =
            mapOf(
                oktober(2017) to inntektBeloep,
                november(2017) to inntektBeloep.minus(100),
                desember(2017) to inntektBeloep.plus(100),
            )

        val forespoersel =
            Forespoersel(
                orgnr = orgnr,
                fnr = Fnr.genererGyldig(),
                vedtaksperiodeId = UUID.randomUUID(),
                sykmeldingsperioder =
                    listOf(
                        1.januar til 22.januar,
                    ),
                egenmeldingsperioder = emptyList(),
                bestemmendeFravaersdager = mapOf(orgnr to 1.januar),
                forespurtData = mockForespurtData(),
                erBesvart = false,
                erBegrenset = false,
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
                erBegrenset = false,
            )
    }
}
