package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verifySequence
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.kafkatopic.pri.Pri
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.rr.test.mockConnectToRapid
import no.nav.hag.simba.utils.rr.test.sendJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class HentForespoerslerForVedtaksperiodeIdListeRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockProducer = mockk<Producer>()

        mockConnectToRapid(testRapid) {
            listOf(
                HentForespoerslerForVedtaksperiodeIdListeRiver(mockProducer),
            )
        }

        test("Ved behov om forespørsler på rapid-topic publiseres behov om forespørsler på pri-topic") {
            // Må bare returnere en Result med gyldig JSON
            every { mockProducer.send(any(), any<Map<Pri.Key, JsonElement>>()) } just Runs

            val expectedEvent = EventName.FORESPOERSLER_REQUESTED
            val expectedKontekstId = UUID.randomUUID()
            val expectedVedtaksperiodeIdListe = listOf(UUID.randomUUID(), UUID.randomUUID())

            testRapid.sendJson(
                Key.EVENT_NAME to expectedEvent.toJson(),
                Key.BEHOV to BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE.toJson(),
                Key.KONTEKST_ID to expectedKontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.VEDTAKSPERIODE_ID_LISTE to expectedVedtaksperiodeIdListe.toJson(UuidSerializer),
                    ).toJson(),
            )

            testRapid.inspektør.size shouldBeExactly 0

            verifySequence {
                mockProducer.send(
                    key = any(),
                    message =
                        mapOf(
                            Pri.Key.BEHOV to Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE.toJson(Pri.BehovType.serializer()),
                            Pri.Key.VEDTAKSPERIODE_ID_LISTE to expectedVedtaksperiodeIdListe.toJson(UuidSerializer),
                            Pri.Key.BOOMERANG to
                                mapOf(
                                    Key.EVENT_NAME to expectedEvent.toJson(),
                                    Key.KONTEKST_ID to expectedKontekstId.toJson(),
                                    Key.DATA to
                                        mapOf(
                                            Key.VEDTAKSPERIODE_ID_LISTE to expectedVedtaksperiodeIdListe.toJson(UuidSerializer),
                                        ).toJson(),
                                ).toJson(),
                        ),
                )
            }
        }
    })
