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
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
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
            val personer =
                mapOf(
                    olaFnr to
                        Person(
                            fnr = olaFnr,
                            navn = "Ola Normann",
                            foedselsdato = 13.juni(1956),
                        ),
                )

            val innkommendeMelding = Mock.innkommendeMelding(listOf(olaFnr))

            coEvery { mockPdlClient.personBolk(any()) } returns listOf(Mock.fullPerson("Ola", olaFnr))

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
                    Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FNR_LISTE to listOf(olaFnr).toJson(Fnr.serializer()),
                            Key.PERSONER to personer.toJson(personMapSerializer),
                        ).toJson(),
                )

            coVerifySequence {
                mockPdlClient.personBolk(listOf(olaFnr.verdi))
            }
        }

        test("finner flere personer") {
            val olaFnr = Fnr.genererGyldig()
            val kariFnr = Fnr.genererGyldig(somDnr = true)
            val personer =
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
                )

            val innkommendeMelding = Mock.innkommendeMelding(listOf(olaFnr, kariFnr))

            coEvery {
                mockPdlClient.personBolk(any())
            } returns
                listOf(
                    Mock.fullPerson("Ola", olaFnr),
                    Mock.fullPerson("Kari", kariFnr),
                )

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
                    Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FNR_LISTE to listOf(olaFnr, kariFnr).toJson(Fnr.serializer()),
                            Key.PERSONER to personer.toJson(personMapSerializer),
                        ).toJson(),
                )

            coVerifySequence {
                mockPdlClient.personBolk(listOf(olaFnr.verdi, kariFnr.verdi))
            }
        }

        test("returnerer kun personer som blir funnet") {
            val olaFnr = Fnr.genererGyldig()
            val kariFnr = Fnr.genererGyldig()
            val personer =
                mapOf(
                    kariFnr to
                        Person(
                            fnr = kariFnr,
                            navn = "Kari Normann",
                            foedselsdato = 13.juni(1956),
                        ),
                )

            val innkommendeMelding = Mock.innkommendeMelding(listOf(olaFnr, kariFnr))

            coEvery { mockPdlClient.personBolk(any()) } returns listOf(Mock.fullPerson("Kari", kariFnr))

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
                    Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FNR_LISTE to listOf(olaFnr, kariFnr).toJson(Fnr.serializer()),
                            Key.PERSONER to personer.toJson(personMapSerializer),
                        ).toJson(),
                )

            coVerifySequence {
                mockPdlClient.personBolk(listOf(olaFnr.verdi, kariFnr.verdi))
            }
        }

        test("sender med forespoerselId dersom det finnes i utløsende melding") {
            val forespoerselId = UUID.randomUUID()
            val olaFnr = Fnr.genererGyldig()
            val personer =
                mapOf(
                    olaFnr to
                        Person(
                            fnr = olaFnr,
                            navn = "Ola Normann",
                            foedselsdato = 13.juni(1956),
                        ),
                )

            val innkommendeMelding = Mock.innkommendeMelding(listOf(olaFnr), forespoerselId)

            coEvery { mockPdlClient.personBolk(any()) } returns listOf(Mock.fullPerson("Ola", olaFnr))

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
                    Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                            Key.FNR_LISTE to listOf(olaFnr).toJson(Fnr.serializer()),
                            Key.PERSONER to personer.toJson(personMapSerializer),
                        ).toJson(),
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
                    "melding med data som flagg" to Pair(Key.DATA, "".toJson()),
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
    fun innkommendeMelding(
        fnrListe: List<Fnr>,
        forespoerselId: UUID? = null,
    ): Melding =
        Melding(
            eventName = EventName.TRENGER_REQUESTED,
            behovType = BehovType.HENT_PERSONER,
            transaksjonId = UUID.randomUUID(),
            data =
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId?.toJson(),
                    Key.FNR_LISTE to fnrListe.toJson(Fnr.serializer()),
                ).mapValuesNotNull { it },
            fnrListe = fnrListe,
        )

    fun Melding.toMap(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to behovType.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to data.toJson(),
        )

    val fail =
        Fail(
            feilmelding = "They have a cave troll.",
            event = EventName.TRENGER_REQUESTED,
            transaksjonId = UUID.randomUUID(),
            forespoerselId = null,
            utloesendeMelding = JsonNull,
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
