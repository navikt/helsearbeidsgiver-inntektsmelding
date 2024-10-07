package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.verify
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Arbeidsgiverperiode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.felles.test.json.lesBehov
import no.nav.helsearbeidsgiver.felles.test.json.plusData
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import no.nav.helsearbeidsgiver.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.message
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.juli
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

class InnsendingServiceTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockRedis = MockRedis(RedisPrefix.Innsending)

        ServiceRiverStateless(
            InnsendingService(testRapid, mockRedis.store),
        ).connect(testRapid)

        beforeEach {
            testRapid.reset()
            clearAllMocks()
            mockRedis.setup()
        }

        test("nytt inntektsmeldingskjema lagres og sendes videre til beriking") {
            val transaksjonId = UUID.randomUUID()

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
                Mock
                    .steg0(transaksjonId)
                    .plusData(
                        Key.SKJEMA_INNTEKTSMELDING to nyttSkjema.toJson(SkjemaInntektsmelding.serializer()),
                    ),
            )

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.message(0).lesBehov() shouldBe BehovType.LAGRE_IM_SKJEMA

            testRapid.sendJson(
                Mock.steg1(transaksjonId).plusData(
                    Key.SKJEMA_INNTEKTSMELDING to nyttSkjema.toJson(SkjemaInntektsmelding.serializer()),
                ),
            )

            testRapid.inspektør.size shouldBeExactly 2
            testRapid.message(1).toMap().also {
                Key.EVENT_NAME.lesOrNull(EventName.serializer(), it) shouldBe EventName.INNTEKTSMELDING_SKJEMA_LAGRET
                Key.UUID.lesOrNull(UuidSerializer, it) shouldBe transaksjonId

                val data = it[Key.DATA]?.toMap().orEmpty()
                Key.ARBEIDSGIVER_FNR.lesOrNull(Fnr.serializer(), data) shouldBe Mock.avsender.fnr
                Key.SKJEMA_INNTEKTSMELDING.lesOrNull(SkjemaInntektsmelding.serializer(), data) shouldBe nyttSkjema
                Key.INNSENDING_ID.lesOrNull(Long.serializer(), data) shouldBe Mock.INNSENDING_ID
            }

            verify {
                mockRedis.store.set(
                    RedisKey.of(transaksjonId),
                    ResultJson(
                        success = nyttSkjema.toJson(SkjemaInntektsmelding.serializer()),
                    ).toJson(ResultJson.serializer()),
                )
            }
        }

        test("duplikat skjema sendes _ikke_ videre til beriking") {
            val transaksjonId = UUID.randomUUID()

            testRapid.sendJson(Mock.steg0(transaksjonId))

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.message(0).lesBehov() shouldBe BehovType.LAGRE_IM_SKJEMA

            testRapid.sendJson(
                Mock
                    .steg1(transaksjonId)
                    .plusData(Key.ER_DUPLIKAT_IM to true.toJson(Boolean.serializer())),
            )

            testRapid.inspektør.size shouldBeExactly 1

            verify {
                mockRedis.store.set(
                    RedisKey.of(transaksjonId),
                    ResultJson(
                        success = Mock.skjema.toJson(SkjemaInntektsmelding.serializer()),
                    ).toJson(ResultJson.serializer()),
                )
            }
        }

        test("svar med feilmelding ved feil") {

            val transaksjonId = UUID.randomUUID()

            val feilmelding = "Databasen er smekk full."

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

            testRapid.sendJson(Mock.steg0(transaksjonId))

            testRapid.sendJson(
                Fail(
                    feilmelding = feilmelding,
                    event = EventName.INSENDING_STARTED,
                    transaksjonId = transaksjonId,
                    forespoerselId = nyttSkjema.forespoerselId,
                    utloesendeMelding =
                        JsonObject(
                            mapOf(
                                Key.BEHOV.toString() to BehovType.LAGRE_IM_SKJEMA.toJson(),
                            ),
                        ),
                ).tilMelding(),
            )

            testRapid.inspektør.size shouldBeExactly 1

            verify {
                mockRedis.store.set(
                    RedisKey.of(transaksjonId),
                    ResultJson(
                        failure = feilmelding.toJson(),
                    ).toJson(ResultJson.serializer()),
                )
            }
        }
    })

private object Mock {
    const val INNSENDING_ID = 1L

    val avsender =
        Fnr.genererGyldig().let {
            Person(
                fnr = it,
                navn = "Skrue McDuck",
                foedselsdato = Person.foedselsdato(it),
            )
        }

    val skjema = mockSkjemaInntektsmelding()

    fun steg0(transaksjonId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.ARBEIDSGIVER_FNR to avsender.fnr.toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(SkjemaInntektsmelding.serializer()),
                ).toJson(),
        )

    fun steg1(transaksjonId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.ARBEIDSGIVER_FNR to avsender.fnr.toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(SkjemaInntektsmelding.serializer()),
                    Key.ER_DUPLIKAT_IM to false.toJson(Boolean.serializer()),
                    Key.INNSENDING_ID to INNSENDING_ID.toJson(Long.serializer()),
                ).toJson(),
        )
}
