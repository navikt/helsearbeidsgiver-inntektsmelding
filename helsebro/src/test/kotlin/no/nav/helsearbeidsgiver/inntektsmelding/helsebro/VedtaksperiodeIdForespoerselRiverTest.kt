package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class VedtaksperiodeIdForespoerselRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockPriProducer = mockk<PriProducer>()

        HentForespoerslerForVedtaksperiodeIdListeRiver(mockPriProducer).connect(testRapid)

        test("Ved behov om forespørsler på rapid-topic publiseres behov om forespørsler på pri-topic") {
            // Må bare returnere en Result med gyldig JSON
            every { mockPriProducer.send(*anyVararg<Pair<Pri.Key, JsonElement>>()) } returns Result.success(JsonNull)

            val expectedEvent = EventName.FORESPOERSLER_REQUESTED
            val expectedTransaksjonId = UUID.randomUUID()
            val expectedVedtaksperiodeIdListe = listOf(UUID.randomUUID(), UUID.randomUUID())

            testRapid.sendJson(
                Key.EVENT_NAME to expectedEvent.toJson(),
                Key.BEHOV to BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE.toJson(),
                Key.UUID to expectedTransaksjonId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.VEDTAKSPERIODE_ID_LISTE to expectedVedtaksperiodeIdListe.toJson(UuidSerializer),
                    ).toJson(),
            )

            testRapid.inspektør.size shouldBeExactly 0

            verifySequence {
                mockPriProducer.send(
                    Pri.Key.BEHOV to Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE.toJson(Pri.BehovType.serializer()),
                    Pri.Key.VEDTAKSPERIODE_ID_LISTE to expectedVedtaksperiodeIdListe.toJson(UuidSerializer),
                    Pri.Key.BOOMERANG to
                        mapOf(
                            Key.EVENT_NAME to expectedEvent.toJson(),
                            Key.UUID to expectedTransaksjonId.toJson(),
                            Key.DATA to
                                mapOf(
                                    Key.VEDTAKSPERIODE_ID_LISTE to expectedVedtaksperiodeIdListe.toJson(UuidSerializer),
                                ).toJson(),
                        ).toJson(),
                )
            }
        }
    })
