package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.altinn.AltinnOrganisasjon
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.altinn.Mock.toMap
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

class AltinnRiverTest :
    FunSpec({
        val testRapid = TestRapid()

        val mockAltinnClient = mockk<AltinnClient>(relaxed = true)

        AltinnRiver(mockAltinnClient).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        test("henter organisasjonsrettigheter med id fra behov") {
            val innkommendeMelding = Mock.innkommendeMelding()

            coEvery { mockAltinnClient.hentRettighetOrganisasjoner(any()) } returns Mock.altinnOrganisasjoner

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            val altinnOrgnr =
                Mock.altinnOrganisasjoner
                    .mapNotNull { it.orgnr }
                    .toSet()

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
                    Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                    Key.DATA to
                        innkommendeMelding.data
                            .plus(
                                Key.ORG_RETTIGHETER to altinnOrgnr.toJson(String.serializer().set()),
                            ).toJson(),
                )

            coVerifySequence {
                mockAltinnClient.hentRettighetOrganisasjoner(innkommendeMelding.fnr.verdi)
            }
        }

        test("håndterer feil") {
            val innkommendeMelding = Mock.innkommendeMelding()

            val innkommendeJsonMap = innkommendeMelding.toMap()

            val forventetFail =
                Fail(
                    feilmelding = "Klarte ikke hente organisasjonsrettigheter fra Altinn.",
                    event = innkommendeMelding.eventName,
                    transaksjonId = innkommendeMelding.transaksjonId,
                    forespoerselId = null,
                    utloesendeMelding = innkommendeJsonMap.toJson(),
                )

            coEvery { mockAltinnClient.hentRettighetOrganisasjoner(any()) } throws NullPointerException()

            testRapid.sendJson(innkommendeJsonMap)

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

            coVerifySequence {
                mockAltinnClient.hentRettighetOrganisasjoner(innkommendeMelding.fnr.verdi)
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med uønsket behov" to Pair(Key.BEHOV, BehovType.VIRKSOMHET.toJson()),
                    "melding med data som flagg" to Pair(Key.DATA, "".toJson()),
                    "melding med fail" to Pair(Key.FAIL, Mock.fail.toJson(Fail.serializer())),
                ),
            ) { uoensketKeyMedVerdi ->
                testRapid.sendJson(
                    Mock
                        .innkommendeMelding()
                        .toMap()
                        .plus(uoensketKeyMedVerdi),
                )

                testRapid.inspektør.size shouldBeExactly 0

                coVerify(exactly = 0) {
                    mockAltinnClient.hentRettighetOrganisasjoner(any())
                }
            }
        }
    })

private object Mock {
    fun innkommendeMelding(): Melding {
        val fnr = Fnr.genererGyldig()

        return Melding(
            eventName = EventName.AKTIVE_ORGNR_REQUESTED,
            behovType = BehovType.ARBEIDSGIVERE,
            transaksjonId = UUID.randomUUID(),
            data =
                mapOf(
                    Key.ARBEIDSGIVER_FNR to fnr.toJson(),
                ),
            fnr = fnr,
        )
    }

    fun Melding.toMap(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to behovType.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.ARBEIDSGIVER_FNR to fnr.toJson(),
                ).toJson(),
        )

    val fail =
        Fail(
            feilmelding = "One does not simply walk into Mordor.",
            event = EventName.AKTIVE_ORGNR_REQUESTED,
            transaksjonId = UUID.randomUUID(),
            forespoerselId = null,
            utloesendeMelding = JsonNull,
        )

    val altinnOrganisasjoner =
        setOf(
            AltinnOrganisasjon(
                navn = "Pippin's Breakfast & Breakfast",
                type = "gluttonous",
            ),
        )
}
