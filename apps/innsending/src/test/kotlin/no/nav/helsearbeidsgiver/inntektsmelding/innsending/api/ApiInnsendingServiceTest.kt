package no.nav.helsearbeidsgiver.inntektsmelding.innsending.api

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Arbeidsgiverperiode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.felles.test.json.lesBehov
import no.nav.helsearbeidsgiver.felles.test.json.plusData
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespoersel
import no.nav.helsearbeidsgiver.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.message
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.juli
import no.nav.helsearbeidsgiver.utils.test.date.kl
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

class ApiInnsendingServiceTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockRedisStore = mockk<RedisStore>(relaxed = true)

        ServiceRiverStateless(
            ApiInnsendingService(testRapid, mockRedisStore),
        ).connect(testRapid)

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("nytt inntektsmeldingskjema lagres og sendes videre til beriking") {
            val kontekstId = UUID.randomUUID()

            val nyttSkjema =
                Mock.skjema.let { skjema ->
                    skjema.copy(
                        agp =
                            skjema.agp?.let { agp ->
                                Arbeidsgiverperiode(
                                    perioder = agp.perioder,
                                    egenmeldinger = listOf(13.juli til 31.juli),
                                    redusertLoennIAgp = agp.redusertLoennIAgp,
                                )
                            },
                    )
                }

            testRapid.sendJson(
                Mock.steg0(kontekstId).plusData(
                    Key.SKJEMA_INNTEKTSMELDING to nyttSkjema.toJson(SkjemaInntektsmelding.serializer()),
                ),
            )

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.message(0).lesBehov() shouldBe BehovType.HENT_TRENGER_IM

            testRapid.sendJson(
                Mock.steg1(kontekstId).plusData(
                    Key.SKJEMA_INNTEKTSMELDING to nyttSkjema.toJson(SkjemaInntektsmelding.serializer()),
                ),
            )

            testRapid.inspektør.size shouldBeExactly 2
            testRapid.message(1).lesBehov() shouldBe BehovType.LAGRE_IM_SKJEMA

            testRapid.sendJson(
                Mock.steg2(kontekstId).plusData(
                    Key.SKJEMA_INNTEKTSMELDING to nyttSkjema.toJson(SkjemaInntektsmelding.serializer()),
                ),
            )

            testRapid.inspektør.size shouldBeExactly 3
            testRapid.message(2).toMap().also {
                Key.EVENT_NAME.lesOrNull(EventName.serializer(), it) shouldBe EventName.INNTEKTSMELDING_SKJEMA_LAGRET
                Key.KONTEKST_ID.lesOrNull(UuidSerializer, it) shouldBe kontekstId

                val data = it[Key.DATA]?.toMap().orEmpty()
                Key.ARBEIDSGIVER_FNR.lesOrNull(Fnr.serializer(), data) shouldBe Mock.avsender.fnr
                Key.SKJEMA_INNTEKTSMELDING.lesOrNull(SkjemaInntektsmelding.serializer(), data) shouldBe nyttSkjema
                Key.INNSENDING_ID.lesOrNull(Long.serializer(), data) shouldBe Mock.INNSENDING_ID
            }

            verify {
                mockRedisStore.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success = nyttSkjema.forespoerselId.toJson(),
                    ),
                )
            }
        }

        test("duplikat skjema sendes _ikke_ videre til beriking") {
            val kontekstId = UUID.randomUUID()

            testRapid.sendJson(Mock.steg0(kontekstId))

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.message(0).lesBehov() shouldBe BehovType.HENT_TRENGER_IM

            testRapid.sendJson(Mock.steg1(kontekstId))

            testRapid.inspektør.size shouldBeExactly 2
            testRapid.message(1).lesBehov() shouldBe BehovType.LAGRE_IM_SKJEMA

            testRapid.sendJson(
                Mock.steg2(kontekstId).plusData(
                    Key.ER_DUPLIKAT_IM to true.toJson(Boolean.serializer()),
                ),
            )

            testRapid.inspektør.size shouldBeExactly 2

            verify {
                mockRedisStore.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success = Mock.skjema.forespoerselId.toJson(),
                    ),
                )
            }
        }

        test("svar med feilmelding ved feil") {
            val fail =
                mockFail(
                    feilmelding = "Databasen er smekk full.",
                    eventName = EventName.INSENDING_STARTED,
                    behovType = BehovType.HENT_TRENGER_IM,
                )

            testRapid.sendJson(Mock.steg0(fail.kontekstId))

            testRapid.sendJson(fail.tilMelding())

            testRapid.inspektør.size shouldBeExactly 1

            verify {
                mockRedisStore.skrivResultat(
                    fail.kontekstId,
                    ResultJson(
                        failure = fail.feilmelding.toJson(),
                    ),
                )
            }
        }
    })

private object Mock {
    const val INNSENDING_ID = 1L

    val avsender =
        Person(
            fnr = Fnr.genererGyldig(),
            navn = "Skrue McDuck",
        )

    val skjema = mockSkjemaInntektsmelding()
    val mottatt = 15.august.kl(12, 0, 0, 0)

    fun steg0(kontekstId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.ARBEIDSGIVER_FNR to avsender.fnr.toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(SkjemaInntektsmelding.serializer()),
                    Key.MOTTATT to mottatt.toJson(),
                ).toJson(),
        )

    fun steg1(kontekstId: UUID): Map<Key, JsonElement> =
        steg0(kontekstId).plusData(
            Key.FORESPOERSEL_SVAR to mockForespoersel().toJson(Forespoersel.serializer()),
        )

    fun steg2(kontekstId: UUID): Map<Key, JsonElement> =
        steg1(kontekstId).plusData(
            mapOf(
                Key.ER_DUPLIKAT_IM to false.toJson(Boolean.serializer()),
                Key.INNSENDING_ID to INNSENDING_ID.toJson(Long.serializer()),
            ),
        )
}
