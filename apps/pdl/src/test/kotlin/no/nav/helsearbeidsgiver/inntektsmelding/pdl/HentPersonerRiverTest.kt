package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.pdl.Mock.toMap
import no.nav.helsearbeidsgiver.pdl.PdlClient
import no.nav.helsearbeidsgiver.pdl.domene.FullPerson
import no.nav.helsearbeidsgiver.pdl.domene.PersonNavn
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.juni
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

class HentPersonerRiverTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockPdlClient = mockk<PdlClient>()

        HentPersonerRiver(mockPdlClient).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        test("finner én person") {
            val olaFnr = Fnr.genererGyldig()
            val personer =
                mapOf(
                    olaFnr to Person(olaFnr, "Ola Normann"),
                )

            val innkommendeMelding = Mock.innkommendeMelding(setOf(olaFnr))

            coEvery { mockPdlClient.personBolk(any()) } returns listOf(Mock.fullPerson("Ola", olaFnr))

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly Mock.forventetUtgaaendeMelding(innkommendeMelding, personer)

            coVerifySequence {
                mockPdlClient.personBolk(listOf(olaFnr.verdi))
            }
        }

        test("finner flere personer") {
            val olaFnr = Fnr.genererGyldig()
            val kariFnr = Fnr.genererGyldig(somDnr = true)
            val personer =
                mapOf(
                    olaFnr to Person(olaFnr, "Ola Normann"),
                    kariFnr to Person(kariFnr, "Kari Normann"),
                )

            val innkommendeMelding = Mock.innkommendeMelding(setOf(olaFnr, kariFnr))

            coEvery {
                mockPdlClient.personBolk(any())
            } returns
                listOf(
                    Mock.fullPerson("Ola", olaFnr),
                    Mock.fullPerson("Kari", kariFnr),
                )

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly Mock.forventetUtgaaendeMelding(innkommendeMelding, personer)

            coVerifySequence {
                mockPdlClient.personBolk(listOf(olaFnr.verdi, kariFnr.verdi))
            }
        }

        test("returnerer kun personer som blir funnet") {
            val olaFnr = Fnr.genererGyldig()
            val kariFnr = Fnr.genererGyldig()
            val personer =
                mapOf(
                    kariFnr to Person(kariFnr, "Kari Normann"),
                )

            val innkommendeMelding = Mock.innkommendeMelding(setOf(olaFnr, kariFnr))

            coEvery { mockPdlClient.personBolk(any()) } returns listOf(Mock.fullPerson("Kari", kariFnr))

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly Mock.forventetUtgaaendeMelding(innkommendeMelding, personer)

            coVerifySequence {
                mockPdlClient.personBolk(listOf(olaFnr.verdi, kariFnr.verdi))
            }
        }

        test("håndterer ukjente feil") {
            val randomFnr = Fnr.genererGyldig()

            val innkommendeMelding = Mock.innkommendeMelding(setOf(randomFnr))

            val innkommendeJsonMap = innkommendeMelding.toMap()

            val forventetFail =
                Fail(
                    feilmelding = "Klarte ikke hente personer fra PDL.",
                    kontekstId = innkommendeMelding.transaksjonId,
                    utloesendeMelding = innkommendeJsonMap,
                )

            coEvery { mockPdlClient.personBolk(any()) } throws IllegalArgumentException("Finner bare brødristere!")

            testRapid.sendJson(innkommendeJsonMap)

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()

            coVerifySequence {
                mockPdlClient.personBolk(listOf(randomFnr.verdi))
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med uønsket behov" to Pair(Key.BEHOV, BehovType.HENT_VIRKSOMHET_NAVN.toJson()),
                    "melding med data som flagg" to Pair(Key.DATA, "".toJson()),
                    "melding med fail" to Pair(Key.FAIL, Mock.fail.toJson(Fail.serializer())),
                ),
            ) { uoensketKeyMedVerdi ->
                val fnrListe = setOf(Fnr.genererGyldig())

                testRapid.sendJson(
                    Mock
                        .innkommendeMelding(fnrListe)
                        .toMap()
                        .plus(uoensketKeyMedVerdi),
                )

                testRapid.inspektør.size shouldBeExactly 0

                coVerify(exactly = 0) {
                    mockPdlClient.personBolk(any())
                }
            }
        }
    })

private object Mock {
    fun innkommendeMelding(fnrListe: Set<Fnr>): Melding {
        val svarKafkaKey = KafkaKey(UUID.randomUUID())

        return Melding(
            eventName = EventName.TRENGER_REQUESTED,
            behovType = BehovType.HENT_PERSONER,
            transaksjonId = UUID.randomUUID(),
            data =
                mapOf(
                    Key.SVAR_KAFKA_KEY to svarKafkaKey.toJson(),
                    Key.FNR_LISTE to fnrListe.toJson(Fnr.serializer()),
                ),
            svarKafkaKey = svarKafkaKey,
            fnrListe = fnrListe,
        )
    }

    fun Melding.toMap(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to behovType.toJson(),
            Key.KONTEKST_ID to transaksjonId.toJson(),
            Key.DATA to data.toJson(),
        )

    fun forventetUtgaaendeMelding(
        innkommendeMelding: Melding,
        personer: Map<Fnr, Person>,
    ): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
            Key.KONTEKST_ID to innkommendeMelding.transaksjonId.toJson(),
            Key.DATA to
                innkommendeMelding.data
                    .plus(
                        Key.PERSONER to personer.toJson(personMapSerializer),
                    ).toJson(),
        )

    val fail = mockFail("They have a cave troll.", EventName.TRENGER_REQUESTED)

    fun fullPerson(
        fornavn: String,
        fnr: Fnr,
    ): FullPerson =
        FullPerson(
            navn =
                PersonNavn(
                    fornavn = fornavn,
                    mellomnavn = null,
                    etternavn = "Normann",
                ),
            foedselsdato = 13.juni(1956),
            ident = fnr.verdi,
        )
}
