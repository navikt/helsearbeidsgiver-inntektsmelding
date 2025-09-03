package no.nav.helsearbeidsgiver.inntektsmelding.trengerservice

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Forespoersel
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.test.json.lesBehov
import no.nav.hag.simba.utils.felles.test.mock.mockFail
import no.nav.hag.simba.utils.felles.test.mock.mockForespoersel
import no.nav.hag.simba.utils.rr.service.ServiceRiverStateless
import no.nav.hag.simba.utils.rr.test.firstMessage
import no.nav.hag.simba.utils.rr.test.message
import no.nav.hag.simba.utils.rr.test.mockConnectToRapid
import no.nav.hag.simba.utils.rr.test.sendJson
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.trengerservice.MockHent.forespoersler
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class HentForespoerslerForVedtaksperiodeIdListeServiceTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockRedisStore = mockk<RedisStore>(relaxed = true)

        mockConnectToRapid(testRapid) {
            listOf(
                ServiceRiverStateless(
                    HentForespoerslerForVedtaksperiodeIdListeService(it, mockRedisStore),
                ),
            )
        }

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("forespørsler hentes og svar sendes ut på redis") {
            val kontekstId = UUID.randomUUID()

            testRapid.sendJson(MockHent.steg0(kontekstId))

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.message(0).lesBehov() shouldBe BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE

            testRapid.sendJson(MockHent.steg1(kontekstId))

            testRapid.inspektør.size shouldBeExactly 1

            verify {
                mockRedisStore.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success = forespoersler.toJson(MapSerializer(UuidSerializer, Forespoersel.serializer())),
                    ),
                )
            }
        }

        test("svar med feilmelding ved feil") {
            val fail =
                mockFail(
                    feilmelding = "Teknisk feil, prøv igjen senere.",
                    eventName = EventName.FORESPOERSLER_REQUESTED,
                    behovType = BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE,
                )

            testRapid.sendJson(MockHent.steg0(fail.kontekstId))

            testRapid.sendJson(fail.tilMelding())

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().lesBehov() shouldBe BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE

            verify {
                mockRedisStore.skrivResultat(
                    fail.kontekstId,
                    ResultJson(failure = fail.feilmelding.toJson()),
                )
            }
        }
    })

private object MockHent {
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

    fun steg0(kontekstId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.FORESPOERSLER_REQUESTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.VEDTAKSPERIODE_ID_LISTE to vedtaksperiodeIdListe.toJson(UuidSerializer),
                ).toJson(),
        )

    fun steg1(kontekstId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.FORESPOERSLER_REQUESTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.VEDTAKSPERIODE_ID_LISTE to vedtaksperiodeIdListe.toJson(UuidSerializer),
                    Key.FORESPOERSEL_MAP to
                        forespoersler.toJson(
                            serializer = MapSerializer(UuidSerializer, Forespoersel.serializer()),
                        ),
                ).toJson(),
        )
}
