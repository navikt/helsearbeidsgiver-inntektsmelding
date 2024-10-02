package no.nav.helsearbeidsgiver.inntektsmelding.trengerservice

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.verify
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.felles.test.json.lesBehov
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespoersel
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.message
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.trengerservice.Mock.forespoersler
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class HentForespoerslerForVedtaksperiodeIdListeServiceTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockRedis = MockRedis(RedisPrefix.HentForespoerslerForVedtaksperiodeIdListe)

        ServiceRiverStateless(
            HentForespoerslerForVedtaksperiodeIdListeService(testRapid, mockRedis.store),
        ).connect(testRapid)

        beforeEach {
            testRapid.reset()
            clearAllMocks()
            mockRedis.setup()
        }

        test("forespørsler hentes og svar sendes ut på redis") {
            val transaksjonId = UUID.randomUUID()

            testRapid.sendJson(Mock.steg0(transaksjonId))

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.message(0).lesBehov() shouldBe BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE

            testRapid.sendJson(Mock.steg1(transaksjonId))

            testRapid.inspektør.size shouldBeExactly 1

            verify {
                mockRedis.store.set(
                    RedisKey.of(transaksjonId),
                    ResultJson(
                        success = forespoersler.toJson(MapSerializer(UuidSerializer, Forespoersel.serializer())),
                    ).toJson(ResultJson.serializer()),
                )
            }
        }

        test("svar med feilmelding ved feil") {
            val transaksjonId = UUID.randomUUID()
            val feilmelding = "Teknisk feil, prøv igjen senere."

            testRapid.sendJson(Mock.steg0(transaksjonId))

            testRapid.sendJson(
                Fail(
                    feilmelding = feilmelding,
                    event = EventName.FORESPOERSLER_REQUESTED,
                    transaksjonId = transaksjonId,
                    forespoerselId = null,
                    utloesendeMelding =
                        JsonObject(
                            mapOf(
                                Key.BEHOV.toString() to BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE.toJson(),
                            ),
                        ),
                ).tilMelding(),
            )

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().lesBehov() shouldBe BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE

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
    val vedtaksperiodeId1: UUID = UUID.randomUUID()
    val vedtaksperiodeId2: UUID = UUID.randomUUID()
    val forespoerselId1: UUID = UUID.randomUUID()
    val forespoerselId2: UUID = UUID.randomUUID()

    val vedtaksperiodeIdListe = listOf(vedtaksperiodeId1, vedtaksperiodeId2)

    val forespoersler =
        mapOf(
            forespoerselId1 to mockForespoersel().copy(vedtaksperiodeId = vedtaksperiodeId1),
            forespoerselId2 to mockForespoersel().copy(vedtaksperiodeId = vedtaksperiodeId2),
        )

    fun steg0(transaksjonId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.FORESPOERSLER_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.VEDTAKSPERIODE_ID_LISTE to vedtaksperiodeIdListe.toJson(UuidSerializer),
                ).toJson(),
        )

    fun steg1(transaksjonId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.FORESPOERSLER_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.VEDTAKSPERIODE_ID_LISTE to vedtaksperiodeIdListe.toJson(UuidSerializer),
                    Key.FORESPOERSLER_SVAR to
                        forespoersler.toJson(
                            serializer = MapSerializer(UuidSerializer, Forespoersel.serializer()),
                        ),
                ).toJson(),
        )
}
