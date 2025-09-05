@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.builtins.MapSerializer
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.test.json.lesFail
import no.nav.hag.simba.utils.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.utils.kontrakt.kafkatopic.pri.Pri
import no.nav.hag.simba.utils.rr.test.firstMessage
import no.nav.hag.simba.utils.rr.test.mockConnectToRapid
import no.nav.hag.simba.utils.rr.test.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselListeSvar
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson

class VedtaksperiodeIdForespoerselSvarRiverTest :
    FunSpec({
        val testRapid = TestRapid()

        mockConnectToRapid(testRapid) {
            listOf(
                VedtaksperiodeIdForespoerselSvarRiver(),
            )
        }

        beforeTest {
            testRapid.reset()
        }

        test("Ved suksessfullt svar på behov så publiseres data på simba-rapid") {
            val forespoerselListeSvarMock = mockForespoerselListeSvarMedSuksess()

            val boomerangMap = forespoerselListeSvarMock.boomerang.toMap()
            val eventName = Key.EVENT_NAME.les(EventName.serializer(), boomerangMap)
            val kontekstId = Key.KONTEKST_ID.les(UuidSerializer, boomerangMap)
            val data = boomerangMap[Key.DATA]?.toMap().orEmpty()
            val forespoersler = forespoerselListeSvarMock.resultat.associate { it.forespoerselId to it.toForespoersel() }

            val forventetSvar =
                mapOf(
                    Key.EVENT_NAME to eventName.toJson(),
                    Key.KONTEKST_ID to kontekstId.toJson(),
                    Key.DATA to
                        data
                            .plus(
                                Key.FORESPOERSEL_MAP to
                                    forespoersler.toJson(
                                        serializer = MapSerializer(UuidSerializer, Forespoersel.serializer()),
                                    ),
                            ).toJson(),
                )

            testRapid.sendJson(
                Pri.Key.BEHOV to Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE.toJson(Pri.BehovType.serializer()),
                Pri.Key.LOESNING to forespoerselListeSvarMock.toJson(ForespoerselListeSvar.serializer()),
            )

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetSvar
        }

        test("Ved feil så publiseres feil på simba-rapid") {
            val forespoerselListeSvarMock = mockForespoerselListeSvarMedFeil()

            val boomerangMap = forespoerselListeSvarMock.boomerang.toMap()

            val eventName = Key.EVENT_NAME.les(EventName.serializer(), boomerangMap)
            val kontekstId = Key.KONTEKST_ID.les(UuidSerializer, boomerangMap)
            val data = boomerangMap[Key.DATA]?.toMap().orEmpty()

            val feilmelding = "Klarte ikke hente forespørsler for vedtaksperiode-IDer. Ukjent feil."

            val forventetSvar =
                Fail(
                    feilmelding = feilmelding,
                    kontekstId = kontekstId,
                    utloesendeMelding =
                        mapOf(
                            Key.EVENT_NAME to eventName.toJson(),
                            Key.BEHOV to BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE.toJson(),
                            Key.KONTEKST_ID to kontekstId.toJson(),
                            Key.DATA to data.toJson(),
                        ),
                )

            testRapid.sendJson(
                Pri.Key.BEHOV to Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE.toJson(Pri.BehovType.serializer()),
                Pri.Key.LOESNING to forespoerselListeSvarMock.toJson(ForespoerselListeSvar.serializer()),
            )

            val actual = testRapid.firstMessage().lesFail()

            testRapid.inspektør.size shouldBeExactly 1

            actual shouldBe forventetSvar
        }
    })
