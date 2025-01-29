package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.verify
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.KvitteringResultat
import no.nav.helsearbeidsgiver.felles.domene.LagretInntektsmelding
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
import no.nav.helsearbeidsgiver.utils.test.date.november
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
                    "inntektsmelding hentes" to LagretInntektsmelding.Skjema("Barbie Roberts", mockSkjemaInntektsmelding(), 6.november.atStartOfDay()),
                    "ekstern inntektsmelding hentes" to LagretInntektsmelding.Ekstern(mockEksternInntektsmelding()),
                    "ingen inntektsmelding funnet" to null,
                ),
            ) { lagret ->
                val kontekstId: UUID = UUID.randomUUID()
                val expectedResult =
                    KvitteringResultat(
                        forespoersel = mockForespoersel(),
                        sykmeldtNavn = "Kenneth Sean Carson",
                        orgNavn = "Mattel",
                        lagret = lagret,
                    )
                val sykmeldtFnr = expectedResult.forespoersel.fnr

                testRapid.sendJson(
                    MockKvittering.steg0(kontekstId),
                )

                testRapid.inspektør.size shouldBeExactly 1
                testRapid.firstMessage().lesBehov() shouldBe BehovType.HENT_TRENGER_IM

                testRapid.sendJson(
                    MockKvittering.steg1(kontekstId, expectedResult.forespoersel),
                )

                testRapid.inspektør.size shouldBeExactly 4
                testRapid.message(1).lesBehov() shouldBe BehovType.HENT_VIRKSOMHET_NAVN
                testRapid.message(2).lesBehov() shouldBe BehovType.HENT_PERSONER
                testRapid.message(3).lesBehov() shouldBe BehovType.HENT_LAGRET_IM

                testRapid.sendJson(
                    MockKvittering.steg2(
                        kontekstId,
                        mapOf(expectedResult.forespoersel.orgnr to expectedResult.orgNavn),
                        mapOf(sykmeldtFnr to Person(sykmeldtFnr, expectedResult.sykmeldtNavn)),
                        expectedResult.lagret,
                    ),
                )

                testRapid.inspektør.size shouldBeExactly 4

                verify {
                    mockRedis.store.skrivResultat(
                        kontekstId,
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
    fun steg0(kontekstId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
                ).toJson(),
        )

    fun steg1(
        kontekstId: UUID,
        forespoersel: Forespoersel,
    ): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_SVAR to forespoersel.toJson(Forespoersel.serializer()),
                ).toJson(),
        )

    fun steg2(
        kontekstId: UUID,
        orgnrMedNavn: Map<Orgnr, String>,
        personer: Map<Fnr, Person>,
        lagret: LagretInntektsmelding?,
    ): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.VIRKSOMHETER to orgnrMedNavn.toJson(orgMapSerializer),
                    Key.PERSONER to personer.toJson(personMapSerializer),
                    Key.LAGRET_INNTEKTSMELDING to
                        ResultJson(
                            success = lagret?.toJson(LagretInntektsmelding.serializer()),
                        ).toJson(),
                ).toJson(),
        )

    val fail = mockFail("Fool of a Took!", EventName.KVITTERING_REQUESTED)
}
