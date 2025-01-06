package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.row
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.verify
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.KvitteringResultat
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.orgMapSerializer
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateful
import no.nav.helsearbeidsgiver.felles.test.json.lesBehov
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import no.nav.helsearbeidsgiver.felles.test.mock.mockEksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespoersel
import no.nav.helsearbeidsgiver.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.message
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class KvitteringServiceTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockRedis = MockRedis(RedisPrefix.Kvittering)

        ServiceRiverStateful(
            KvitteringService(testRapid, mockRedis.store),
        ).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
            mockRedis.setup()
        }

        context("kvittering hentes") {
            withData(
                mapOf(
                    "inntektsmelding hentes" to row("Barbie Roberts", mockSkjemaInntektsmelding(), null),
                    "ekstern inntektsmelding hentes" to row(null, null, mockEksternInntektsmelding()),
                    "ingen inntektsmelding funnet" to row(null, null, null),
                    "begge typer inntektsmelding funnet (skal ikke skje)" to
                        row("Barbie Roberts", mockSkjemaInntektsmelding(), mockEksternInntektsmelding()),
                ),
            ) { (expectedAvsenderNavn, expectedSkjema, expectedEksternInntektsmelding) ->
                val transaksjonId: UUID = UUID.randomUUID()
                val expectedResult =
                    KvitteringResultat(
                        forespoersel = mockForespoersel(),
                        sykmeldtNavn = "Kenneth Sean Carson",
                        avsenderNavn = expectedAvsenderNavn ?: "Ukjent navn",
                        orgNavn = "Mattel",
                        skjema = expectedSkjema,
                        eksternInntektsmelding = expectedEksternInntektsmelding,
                    )
                val sykmeldtFnr = expectedResult.forespoersel.fnr

                testRapid.sendJson(
                    MockKvittering.steg0(transaksjonId),
                )

                testRapid.inspektør.size shouldBeExactly 1
                testRapid.firstMessage().lesBehov() shouldBe BehovType.HENT_TRENGER_IM

                testRapid.sendJson(
                    MockKvittering.steg1(transaksjonId, expectedResult.forespoersel),
                )

                testRapid.inspektør.size shouldBeExactly 4
                testRapid.message(1).lesBehov() shouldBe BehovType.HENT_VIRKSOMHET_NAVN
                testRapid.message(2).lesBehov() shouldBe BehovType.HENT_PERSONER
                testRapid.message(3).lesBehov() shouldBe BehovType.HENT_LAGRET_IM

                testRapid.sendJson(
                    MockKvittering.steg2(
                        transaksjonId,
                        mapOf(expectedResult.forespoersel.orgnr to expectedResult.orgNavn),
                        mapOf(sykmeldtFnr to Person(sykmeldtFnr, expectedResult.sykmeldtNavn)),
                        expectedResult.avsenderNavn,
                        expectedResult.skjema,
                        expectedResult.eksternInntektsmelding,
                    ),
                )

                testRapid.inspektør.size shouldBeExactly 4

                verify {
                    mockRedis.store.skrivResultat(
                        transaksjonId,
                        ResultJson(
                            success = expectedResult.toJson(KvitteringResultat.serializer()),
                        ),
                    )
                }
            }
        }

        test("svarer med feilmelding dersom man ikke klarer å hente inntektsmelding") {
            val expectedFailure =
                ResultJson(
                    failure = MockKvittering.fail.feilmelding.toJson(),
                )

            testRapid.sendJson(
                MockKvittering.steg0(MockKvittering.fail.kontekstId),
            )

            testRapid.sendJson(
                MockKvittering.fail.tilMelding(),
            )

            testRapid.inspektør.size shouldBeExactly 1

            verify {
                mockRedis.store.skrivResultat(MockKvittering.fail.kontekstId, expectedFailure)
            }
        }
    })

private object MockKvittering {
    fun steg0(transaksjonId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.KONTEKST_ID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
                ).toJson(),
        )

    fun steg1(
        transaksjonId: UUID,
        forespoersel: Forespoersel,
    ): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.KONTEKST_ID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_SVAR to forespoersel.toJson(Forespoersel.serializer()),
                ).toJson(),
        )

    fun steg2(
        transaksjonId: UUID,
        orgnrMedNavn: Map<Orgnr, String>,
        personer: Map<Fnr, Person>,
        avsenderNavn: String?,
        skjema: SkjemaInntektsmelding?,
        eksternInntektsmelding: EksternInntektsmelding?,
    ): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.KONTEKST_ID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.VIRKSOMHETER to orgnrMedNavn.toJson(orgMapSerializer),
                    Key.PERSONER to personer.toJson(personMapSerializer),
                    Key.AVSENDER_NAVN to
                        ResultJson(
                            success = avsenderNavn?.toJson(),
                        ).toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to
                        ResultJson(
                            success = skjema?.toJson(SkjemaInntektsmelding.serializer()),
                        ).toJson(),
                    Key.EKSTERN_INNTEKTSMELDING to
                        ResultJson(
                            success = eksternInntektsmelding?.toJson(EksternInntektsmelding.serializer()),
                        ).toJson(),
                ).toJson(),
        )

    val fail = mockFail("Fool of a Took!", EventName.KVITTERING_REQUESTED)
}
