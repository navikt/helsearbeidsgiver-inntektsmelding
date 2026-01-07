package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verifySequence
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.kontrakt.domene.forespoersel.test.mockForespoersel
import no.nav.hag.simba.kontrakt.domene.forespoersel.test.utenPaakrevdAGP
import no.nav.hag.simba.kontrakt.resultat.lagreim.LagreImError
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Person
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.lesOrNull
import no.nav.hag.simba.utils.felles.json.personMapSerializer
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.test.json.lesBehov
import no.nav.hag.simba.utils.felles.test.json.plusData
import no.nav.hag.simba.utils.felles.test.mock.mockFail
import no.nav.hag.simba.utils.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.hag.simba.utils.rr.service.ServiceRiverStateless
import no.nav.hag.simba.utils.rr.test.message
import no.nav.hag.simba.utils.rr.test.mockConnectToRapid
import no.nav.hag.simba.utils.rr.test.sendJson
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Arbeidsgiverperiode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateTimeSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.kl
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

class InnsendingServiceTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockRedisStore = mockk<RedisStore>(relaxed = true)

        mockConnectToRapid(testRapid) {
            listOf(
                ServiceRiverStateless(
                    InnsendingService(it, mockRedisStore),
                ),
            )
        }

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("nytt inntektsmeldingskjema lagres og sendes videre til beriking") {
            val kontekstId = UUID.randomUUID()
            val skjema = mockSkjemaInntektsmelding()

            testRapid.sendJson(Mock.steg0(kontekstId, skjema))

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.message(0).lesBehov() shouldBe BehovType.HENT_TRENGER_IM

            testRapid.sendJson(Mock.steg1(kontekstId, skjema))

            testRapid.inspektør.size shouldBeExactly 2
            testRapid.message(1).lesBehov() shouldBe BehovType.HENT_PERSONER

            testRapid.sendJson(Mock.steg2(kontekstId, skjema))

            testRapid.inspektør.size shouldBeExactly 3
            testRapid.message(2).lesBehov() shouldBe BehovType.LAGRE_IM_SKJEMA

            testRapid.sendJson(Mock.steg3(kontekstId, skjema))

            testRapid.inspektør.size shouldBeExactly 4
            testRapid.message(3).toMap().also {
                Key.EVENT_NAME.lesOrNull(EventName.serializer(), it) shouldBe EventName.INNTEKTSMELDING_SKJEMA_LAGRET
                Key.KONTEKST_ID.lesOrNull(UuidSerializer, it) shouldBe kontekstId

                val data = it[Key.DATA]?.toMap().orEmpty()
                Key.ARBEIDSGIVER_FNR.lesOrNull(Fnr.serializer(), data) shouldBe Mock.avsender.fnr
                Key.FORESPOERSEL_SVAR.lesOrNull(Forespoersel.serializer(), data).shouldNotBeNull()
                Key.SKJEMA_INNTEKTSMELDING.lesOrNull(SkjemaInntektsmelding.serializer(), data) shouldBe skjema
                Key.INNTEKTSMELDING_ID.lesOrNull(UuidSerializer, data).shouldNotBeNull()
                Key.AVSENDER_NAVN.lesOrNull(String.serializer(), data) shouldBe Mock.avsender.navn
                Key.MOTTATT.lesOrNull(LocalDateTimeSerializer, data).shouldNotBeNull()
            }

            verifySequence {
                mockRedisStore.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success = skjema.forespoerselId.toJson(),
                    ),
                )
            }
        }

        test("avviser inntektsmeldingskjema dersom ikke-forespurt AGP er ugyldig") {
            val kontekstId = UUID.randomUUID()
            val forespoersel = mockForespoersel().utenPaakrevdAGP()
            val skjemaMedUgyldigAgp =
                mockSkjemaInntektsmelding().copy(
                    agp =
                        forespoersel.sykmeldingsperioder.minOf { it.fom }.let {
                            Arbeidsgiverperiode(
                                perioder =
                                    listOf(
                                        Periode(
                                            fom = it,
                                            tom = it.plusDays(15),
                                        ),
                                    ),
                                egenmeldinger = emptyList(),
                                redusertLoennIAgp = null,
                            )
                        },
                )

            testRapid.sendJson(
                Mock.steg2(kontekstId, skjemaMedUgyldigAgp).plusData(
                    Key.FORESPOERSEL_SVAR to forespoersel.toJson(),
                ),
            )

            verifySequence {
                mockRedisStore.skrivResultat(
                    kontekstId,
                    ResultJson(
                        failure =
                            LagreImError(
                                feiletValidering = "Arbeidsgiverperioden må indikere at sykmeldt arbeidet i starten av sykmeldingsperioden.",
                            ).toJson(LagreImError.serializer()),
                    ),
                )
            }
        }

        test("duplikat skjema sendes _ikke_ videre til beriking") {
            val kontekstId = UUID.randomUUID()
            val skjema = mockSkjemaInntektsmelding()

            testRapid.sendJson(Mock.steg0(kontekstId, skjema))

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.message(0).lesBehov() shouldBe BehovType.HENT_TRENGER_IM

            testRapid.sendJson(Mock.steg1(kontekstId, skjema))

            testRapid.inspektør.size shouldBeExactly 2
            testRapid.message(1).lesBehov() shouldBe BehovType.HENT_PERSONER

            testRapid.sendJson(Mock.steg2(kontekstId, skjema))

            testRapid.inspektør.size shouldBeExactly 3
            testRapid.message(2).lesBehov() shouldBe BehovType.LAGRE_IM_SKJEMA

            testRapid.sendJson(
                Mock.steg3(kontekstId, skjema).plusData(
                    Key.ER_DUPLIKAT_IM to true.toJson(Boolean.serializer()),
                ),
            )

            testRapid.inspektør.size shouldBeExactly 3

            verifySequence {
                mockRedisStore.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success = skjema.forespoerselId.toJson(),
                    ),
                )
            }
        }

        test("svar med tomt feilobjekt ved mottatt feil") {
            val fail =
                mockFail(
                    feilmelding = "Databasen er smekk full.",
                    eventName = EventName.INSENDING_STARTED,
                    behovType = BehovType.HENT_TRENGER_IM,
                )

            testRapid.sendJson(Mock.steg0(fail.kontekstId, mockSkjemaInntektsmelding()))

            testRapid.sendJson(fail.tilMelding())

            testRapid.inspektør.size shouldBeExactly 1

            verifySequence {
                mockRedisStore.skrivResultat(
                    fail.kontekstId,
                    ResultJson(
                        failure = LagreImError().toJson(LagreImError.serializer()),
                    ),
                )
            }
        }
    })

private object Mock {
    val avsender =
        Person(
            fnr = Fnr.genererGyldig(),
            navn = "Skrue McDuck",
        )

    fun steg0(
        kontekstId: UUID,
        skjema: SkjemaInntektsmelding,
    ): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.ARBEIDSGIVER_FNR to avsender.fnr.toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(SkjemaInntektsmelding.serializer()),
                    Key.MOTTATT to 15.august.kl(12, 0, 0, 0).toJson(),
                ).toJson(),
        )

    fun steg1(
        kontekstId: UUID,
        skjema: SkjemaInntektsmelding,
    ): Map<Key, JsonElement> =
        steg0(kontekstId, skjema).plusData(
            Key.FORESPOERSEL_SVAR to mockForespoersel().toJson(),
        )

    fun steg2(
        kontekstId: UUID,
        skjema: SkjemaInntektsmelding,
    ): Map<Key, JsonElement> =
        steg1(kontekstId, skjema).plusData(
            Key.PERSONER to mapOf(avsender.fnr to avsender).toJson(personMapSerializer),
        )

    fun steg3(
        kontekstId: UUID,
        skjema: SkjemaInntektsmelding,
    ): Map<Key, JsonElement> =
        steg2(kontekstId, skjema).plusData(
            mapOf(
                Key.INNTEKTSMELDING_ID to UUID.randomUUID().toJson(),
                Key.ER_DUPLIKAT_IM to false.toJson(Boolean.serializer()),
            ),
        )
}
