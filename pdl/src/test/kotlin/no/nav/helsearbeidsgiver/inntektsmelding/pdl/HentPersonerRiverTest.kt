package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.mockk
import io.prometheus.client.CollectorRegistry
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Person
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
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
            CollectorRegistry.defaultRegistry.clear()
        }

        test("finner én person") {
            val olaFnr = Fnr.genererGyldig()

            val innkommendeMelding = Mock.innkommendeMelding(listOf(olaFnr))

            coEvery { mockPdlClient.personBolk(any()) } returns listOf(Mock.fullPerson("Ola", olaFnr))

            testRapid.sendJson(innkommendeMelding.toMap())

            val dataField =
                Key.PERSONER to
                    mapOf(
                        olaFnr to
                            Person(
                                fnr = olaFnr,
                                navn = "Ola Normann",
                                foedselsdato = 13.juni(1956),
                            ),
                    ).toJson(personMapSerializer)

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
                    Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                    Key.DATA to mapOf(dataField).toJson(),
                    dataField,
                )

            coVerifySequence {
                mockPdlClient.personBolk(listOf(olaFnr.verdi))
            }
        }

        test("finner flere personer") {
            val olaFnr = Fnr.genererGyldig()
            val kariFnr = Fnr.genererGyldig(somDnr = true)

            val innkommendeMelding = Mock.innkommendeMelding(listOf(olaFnr, kariFnr))

            coEvery {
                mockPdlClient.personBolk(any())
            } returns
                listOf(
                    Mock.fullPerson("Ola", olaFnr),
                    Mock.fullPerson("Kari", kariFnr),
                )

            testRapid.sendJson(innkommendeMelding.toMap())

            val dataField =
                Key.PERSONER to
                    mapOf(
                        olaFnr to
                            Person(
                                fnr = olaFnr,
                                navn = "Ola Normann",
                                foedselsdato = 13.juni(1956),
                            ),
                        kariFnr to
                            Person(
                                fnr = kariFnr,
                                navn = "Kari Normann",
                                foedselsdato = 13.juni(1956),
                            ),
                    ).toJson(personMapSerializer)

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
                    Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                    Key.DATA to mapOf(dataField).toJson(),
                    dataField,
                )

            coVerifySequence {
                mockPdlClient.personBolk(listOf(olaFnr.verdi, kariFnr.verdi))
            }
        }

        test("returnerer kun personer som blir funnet") {
            val olaFnr = Fnr.genererGyldig()
            val kariFnr = Fnr.genererGyldig()

            val innkommendeMelding = Mock.innkommendeMelding(listOf(olaFnr, kariFnr))

            coEvery { mockPdlClient.personBolk(any()) } returns listOf(Mock.fullPerson("Kari", kariFnr))

            testRapid.sendJson(innkommendeMelding.toMap())

            val dataField =
                Key.PERSONER to
                    mapOf(
                        kariFnr to
                            Person(
                                fnr = kariFnr,
                                navn = "Kari Normann",
                                foedselsdato = 13.juni(1956),
                            ),
                    ).toJson(personMapSerializer)

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
                    Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                    Key.DATA to mapOf(dataField).toJson(),
                    dataField,
                )

            coVerifySequence {
                mockPdlClient.personBolk(listOf(olaFnr.verdi, kariFnr.verdi))
            }
        }

        test("sender med forespoerselId dersom det finnes i utløsende melding") {
            val forespoerselId = UUID.randomUUID()
            val olaFnr = Fnr.genererGyldig()

            val innkommendeMelding = Mock.innkommendeMelding(listOf(olaFnr))

            coEvery { mockPdlClient.personBolk(any()) } returns listOf(Mock.fullPerson("Ola", olaFnr))

            testRapid.sendJson(
                innkommendeMelding
                    .toMap()
                    .plus(Key.FORESPOERSEL_ID to forespoerselId.toJson()),
            )

            val dataFields =
                arrayOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.PERSONER to
                        mapOf(
                            olaFnr to
                                Person(
                                    fnr = olaFnr,
                                    navn = "Ola Normann",
                                    foedselsdato = 13.juni(1956),
                                ),
                        ).toJson(personMapSerializer),
                )

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
                    Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                    Key.DATA to dataFields.toMap().toJson(),
                    *dataFields,
                )
        }

        test("sender med selvbestemtId dersom det finnes i utløsende melding") {
            val selvbestemtId = UUID.randomUUID()
            val olaFnr = Fnr.genererGyldig()

            val innkommendeMelding = Mock.innkommendeMelding(listOf(olaFnr))

            coEvery { mockPdlClient.personBolk(any()) } returns listOf(Mock.fullPerson("Ola", olaFnr))

            testRapid.sendJson(
                innkommendeMelding
                    .toMap()
                    .plus(Key.SELVBESTEMT_ID to selvbestemtId.toJson()),
            )

            val dataFields =
                arrayOf(
                    Key.SELVBESTEMT_ID to selvbestemtId.toJson(),
                    Key.PERSONER to
                        mapOf(
                            olaFnr to
                                Person(
                                    fnr = olaFnr,
                                    navn = "Ola Normann",
                                    foedselsdato = 13.juni(1956),
                                ),
                        ).toJson(personMapSerializer),
                )

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
                    Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                    Key.DATA to dataFields.toMap().toJson(),
                    *dataFields,
                )
        }

        test("håndterer ukjente feil") {
            val forespoerselId = UUID.randomUUID()
            val selvbestemtId = UUID.randomUUID()
            val randomFnr = Fnr.genererGyldig()

            val innkommendeMelding = Mock.innkommendeMelding(listOf(randomFnr))

            val innkommendeJsonMap =
                innkommendeMelding
                    .toMap()
                    .plus(Key.FORESPOERSEL_ID to forespoerselId.toJson())
                    .plus(Key.SELVBESTEMT_ID to selvbestemtId.toJson())

            val forventetFail =
                Fail(
                    feilmelding = "Klarte ikke hente personer fra PDL.",
                    event = innkommendeMelding.eventName,
                    transaksjonId = innkommendeMelding.transaksjonId,
                    forespoerselId = forespoerselId,
                    utloesendeMelding = innkommendeJsonMap.toJson(),
                )

            coEvery { mockPdlClient.personBolk(any()) } throws IllegalArgumentException("Finner bare brødristere!")

            testRapid.sendJson(innkommendeJsonMap)

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                forventetFail
                    .tilMelding()
                    .plus(Key.FORESPOERSEL_ID to forespoerselId.toJson())
                    .plus(Key.SELVBESTEMT_ID to selvbestemtId.toJson())

            coVerifySequence {
                mockPdlClient.personBolk(listOf(randomFnr.verdi))
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med uønsket behov" to Pair(Key.BEHOV, BehovType.HENT_VIRKSOMHET_NAVN.toJson()),
                    "melding med data" to Pair(Key.DATA, "".toJson()),
                    "melding med fail" to Pair(Key.FAIL, Mock.fail.toJson(Fail.serializer())),
                ),
            ) { uoensketKeyMedVerdi ->
                val fnrListe = listOf(Fnr.genererGyldig())

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
    val fail =
        Fail(
            feilmelding = "They have a cave troll.",
            event = EventName.TRENGER_REQUESTED,
            transaksjonId = UUID.randomUUID(),
            forespoerselId = null,
            utloesendeMelding = JsonNull,
        )

    fun innkommendeMelding(fnrListe: List<Fnr>): Melding =
        Melding(
            eventName = EventName.TRENGER_REQUESTED,
            behovType = BehovType.HENT_PERSONER,
            transaksjonId = UUID.randomUUID(),
            fnrListe = fnrListe,
        )

    fun Melding.toMap(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to behovType.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FNR_LISTE to fnrListe.toJson(Fnr.serializer()),
        )

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
