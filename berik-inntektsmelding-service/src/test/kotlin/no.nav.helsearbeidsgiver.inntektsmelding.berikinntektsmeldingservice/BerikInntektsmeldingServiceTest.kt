package no.nav.helsearbeidsgiver.inntektsmelding.berikinntektsmeldingservice

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.orgMapSerializer
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.felles.test.json.lesBehov
import no.nav.helsearbeidsgiver.felles.test.json.lesData
import no.nav.helsearbeidsgiver.felles.test.json.lesEventName
import no.nav.helsearbeidsgiver.felles.test.json.plusData
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespoersel
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingGammeltFormat
import no.nav.helsearbeidsgiver.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.message
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class BerikInntektsmeldingServiceTest :
    FunSpec({

        val testRapid = TestRapid()

        ServiceRiverStateless(
            BerikInntektsmeldingService(testRapid),
        ).connect(testRapid)

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("nytt inntektsmeldingskjema berikes, lagres og sendes videre til journalføring") {
            testRapid.sendJson(Mock.steg0(Mock.transaksjonId))

            // Melding med forventet behov og data sendt for å hente forespørsel
            testRapid.inspektør.size shouldBeExactly 1
            testRapid.message(0).also {
                it.lesBehov() shouldBe BehovType.HENT_TRENGER_IM

                Key.FORESPOERSEL_ID.lesOrNull(
                    serializer = UuidSerializer,
                    melding = it.lesData(),
                ) shouldBe Mock.skjema.forespoerselId
            }

            testRapid.sendJson(Mock.steg1(Mock.transaksjonId))

            // Melding med forventet behov og data sendt for å hente virksomhetsnavn
            testRapid.inspektør.size shouldBeExactly 2
            testRapid.message(1).also {
                it.lesBehov() shouldBe BehovType.HENT_VIRKSOMHET_NAVN

                Key.ORGNR_UNDERENHETER.lesOrNull(
                    serializer = Orgnr.serializer().set(),
                    melding = it.lesData(),
                ) shouldNotBe null
            }

            testRapid.sendJson(Mock.steg2(Mock.transaksjonId))

            // Melding med forventet behov og data sendt for å hente personnavn
            testRapid.inspektør.size shouldBeExactly 3
            testRapid.message(2).also {
                it.lesBehov() shouldBe BehovType.HENT_PERSONER

                Key.FNR_LISTE.lesOrNull(
                    serializer = Fnr.serializer().set(),
                    melding = it.lesData(),
                ) shouldNotBe null
            }

            testRapid.sendJson(Mock.steg3(Mock.transaksjonId))

            // Melding med forventet behov og data sendt for å lagre inntektsmelding
            testRapid.inspektør.size shouldBeExactly 4
            testRapid.message(3).also {
                it.lesBehov() shouldBe BehovType.LAGRE_IM

                val data = it.lesData()
                Key.INNTEKTSMELDING_DOKUMENT.lesOrNull(Inntektsmelding.serializer(), data) shouldNotBe null
                Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, data) shouldBe Mock.skjema.forespoerselId
                Key.INNSENDING_ID.lesOrNull(Long.serializer(), data) shouldBe Mock.innsendingId
            }

            testRapid.sendJson(Mock.steg4(Mock.transaksjonId))

            // Inntektsmelding sendt videre til journalføring med forventet data
            testRapid.inspektør.size shouldBeExactly 5
            testRapid.message(4).also {
                it.lesEventName() shouldBe EventName.INNTEKTSMELDING_MOTTATT
                Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, it.toMap()) shouldBe Mock.skjema.forespoerselId
                Key.INNTEKTSMELDING_DOKUMENT.lesOrNull(Inntektsmelding.serializer(), it.toMap()) shouldNotBe null
                Key.INNSENDING_ID.lesOrNull(Long.serializer(), it.toMap()) shouldBe Mock.innsendingId
            }
        }

        test("duplikat IM sendes _ikke_ videre til journalføring") {
            testRapid.sendJson(
                Mock
                    .steg4(Mock.transaksjonId)
                    .plusData(Key.ER_DUPLIKAT_IM to true.toJson(Boolean.serializer())),
            )

            testRapid.inspektør.size shouldBeExactly 0
        }

        test("skal ved feil ikke foreta seg noe (FeilLytter skal plukke opp og rekjøre meldingen som utløste feilen)") {

            val transaksjonId = UUID.randomUUID()
            testRapid.sendJson(
                Fail(
                    feilmelding = "Detta gikk jo ikke så bra.",
                    event = EventName.INNTEKTSMELDING_SKJEMA_LAGRET,
                    transaksjonId = transaksjonId,
                    forespoerselId = Mock.skjema.forespoerselId,
                    utloesendeMelding =
                        JsonObject(
                            mapOf(
                                Key.BEHOV.toString() to BehovType.HENT_TRENGER_IM.toJson(),
                            ),
                        ),
                ).tilMelding(),
            )

            testRapid.inspektør.size shouldBeExactly 0
        }
    })

private object Mock {
    val transaksjonId = UUID.randomUUID()
    val forespoersel = mockForespoersel()
    val skjema = mockSkjemaInntektsmelding()
    val inntektsmelding = mockInntektsmeldingGammeltFormat()

    val avsender =
        Fnr.genererGyldig().let {
            Person(
                fnr = it,
                navn = "Skrue McDuck",
                foedselsdato = Person.foedselsdato(it),
            )
        }

    val sykmeldt =
        Fnr(forespoersel.fnr).let {
            Person(
                fnr = it,
                navn = "Dolly Duck",
                foedselsdato = Person.foedselsdato(it),
            )
        }

    val orgnrMedNavn = mapOf(Orgnr(forespoersel.orgnr) to "Lasses kasserollesjappe")

    val personer =
        mapOf(
            avsender.fnr to avsender,
            sykmeldt.fnr to sykmeldt,
        )

    val innsendingId = 1L

    val steg0data =
        mapOf(
            Key.ARBEIDSGIVER_FNR to avsender.fnr.toJson(),
            Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(SkjemaInntektsmelding.serializer()),
            Key.INNSENDING_ID to innsendingId.toJson(Long.serializer()),
        )

    val steg1data =
        mapOf(
            Key.FORESPOERSEL_SVAR to forespoersel.toJson(Forespoersel.serializer()),
        )

    val steg2data =
        mapOf(
            Key.VIRKSOMHETER to orgnrMedNavn.toJson(orgMapSerializer),
        )

    val steg3data =
        mapOf(
            Key.PERSONER to personer.toJson(personMapSerializer),
        )

    val steg4data =
        mapOf(
            Key.ER_DUPLIKAT_IM to false.toJson(Boolean.serializer()),
            Key.INNTEKTSMELDING_DOKUMENT to inntektsmelding.toJson(Inntektsmelding.serializer()),
        )

    fun steg0(transaksjonId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_SKJEMA_LAGRET.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to steg0data.toJson(),
        )

    fun steg1(transaksjonId: UUID): Map<Key, JsonElement> = steg0(transaksjonId).plusData(steg1data)

    fun steg2(transaksjonId: UUID): Map<Key, JsonElement> = steg1(transaksjonId).plusData(steg2data)

    fun steg3(transaksjonId: UUID): Map<Key, JsonElement> = steg2(transaksjonId).plusData(steg3data)

    fun steg4(transaksjonId: UUID): Map<Key, JsonElement> = steg3(transaksjonId).plusData(steg4data)
}
