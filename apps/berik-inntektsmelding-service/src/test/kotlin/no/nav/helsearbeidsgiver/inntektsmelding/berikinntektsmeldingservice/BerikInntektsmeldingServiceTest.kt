package no.nav.helsearbeidsgiver.inntektsmelding.berikinntektsmeldingservice

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
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
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.felles.test.json.lesBehov
import no.nav.helsearbeidsgiver.felles.test.json.lesData
import no.nav.helsearbeidsgiver.felles.test.json.lesEventName
import no.nav.helsearbeidsgiver.felles.test.json.plusData
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespoersel
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.message
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.oktober
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
            val transaksjonId = UUID.randomUUID()

            testRapid.sendJson(Mock.steg0(transaksjonId))

            // Melding med forventet behov og data sendt for å hente forespørsel
            testRapid.inspektør.size shouldBeExactly 1
            testRapid.message(0).also {
                it.lesBehov() shouldBe BehovType.HENT_TRENGER_IM

                val data = it.lesData()
                Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, data) shouldBe Mock.skjema.forespoerselId
            }

            testRapid.sendJson(Mock.steg1(transaksjonId))

            // Melding med forventet behov og data sendt for å hente virksomhetsnavn
            testRapid.inspektør.size shouldBeExactly 2
            testRapid.message(1).also {
                it.lesBehov() shouldBe BehovType.HENT_VIRKSOMHET_NAVN

                val data = it.lesData()
                Key.ORGNR_UNDERENHETER.lesOrNull(Orgnr.serializer().set(), data) shouldNotBe null
            }

            testRapid.sendJson(Mock.steg2(transaksjonId))

            // Melding med forventet behov og data sendt for å hente personnavn
            testRapid.inspektør.size shouldBeExactly 3
            testRapid.message(2).also {
                it.lesBehov() shouldBe BehovType.HENT_PERSONER

                val data = it.lesData()
                Key.FNR_LISTE.lesOrNull(Fnr.serializer().set(), data) shouldNotBe null
            }

            testRapid.sendJson(Mock.steg3(transaksjonId))

            // Melding med forventet behov og data sendt for å lagre inntektsmelding
            testRapid.inspektør.size shouldBeExactly 4
            testRapid.message(3).also {
                it.lesBehov() shouldBe BehovType.LAGRE_IM

                val data = it.lesData()
                Key.INNTEKTSMELDING.lesOrNull(Inntektsmelding.serializer(), data) shouldNotBe null
                Key.BESTEMMENDE_FRAVAERSDAG.lesOrNull(LocalDateSerializer, data) shouldNotBe null
                Key.INNSENDING_ID.lesOrNull(Long.serializer(), data) shouldBe Mock.INNSENDING_ID
            }

            testRapid.sendJson(Mock.steg4(transaksjonId))

            // Inntektsmelding sendt videre til journalføring med forventet data
            testRapid.inspektør.size shouldBeExactly 5
            testRapid.message(4).also {
                it.lesEventName() shouldBe EventName.INNTEKTSMELDING_MOTTATT

                val data = it.lesData()
                Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, data) shouldBe Mock.skjema.forespoerselId
                Key.INNTEKTSMELDING.lesOrNull(Inntektsmelding.serializer(), data) shouldNotBe null
                Key.BESTEMMENDE_FRAVAERSDAG.lesOrNull(LocalDateSerializer, data) shouldNotBe null
                Key.INNSENDING_ID.lesOrNull(Long.serializer(), data) shouldBe Mock.INNSENDING_ID
            }
        }

        test("duplikat IM sendes _ikke_ videre til journalføring") {
            testRapid.sendJson(
                Mock
                    .steg4(UUID.randomUUID())
                    .plusData(Key.ER_DUPLIKAT_IM to true.toJson(Boolean.serializer())),
            )

            testRapid.inspektør.size shouldBeExactly 0
        }

        test("skal ved feil ikke foreta seg noe (FeilLytter skal plukke opp og rekjøre meldingen som utløste feilen)") {
            val fail =
                mockFail(
                    feilmelding = "Detta gikk jo ikke så bra.",
                    eventName = EventName.INNTEKTSMELDING_SKJEMA_LAGRET,
                    behovType = BehovType.HENT_TRENGER_IM,
                )

            testRapid.sendJson(fail.tilMelding())

            testRapid.inspektør.size shouldBeExactly 0
        }
    })

private object Mock {
    const val INNSENDING_ID = 1L

    val skjema = mockSkjemaInntektsmelding()

    private val forespoersel = mockForespoersel()

    private val avsender =
        Person(
            fnr = Fnr.genererGyldig(),
            navn = "Skrue McDuck",
        )

    private val sykmeldt =
        Person(
            fnr = forespoersel.fnr,
            navn = "Dolly Duck",
        )

    private val orgnrMedNavn = mapOf(forespoersel.orgnr to "Lasses kasserollesjappe")

    private val personer =
        mapOf(
            avsender.fnr to avsender,
            sykmeldt.fnr to sykmeldt,
        )

    fun steg0(transaksjonId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_SKJEMA_LAGRET.toJson(),
            Key.KONTEKST_ID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.ARBEIDSGIVER_FNR to avsender.fnr.toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(SkjemaInntektsmelding.serializer()),
                    Key.INNSENDING_ID to INNSENDING_ID.toJson(Long.serializer()),
                ).toJson(),
        )

    fun steg1(transaksjonId: UUID): Map<Key, JsonElement> =
        steg0(transaksjonId).plusData(
            Key.FORESPOERSEL_SVAR to forespoersel.toJson(Forespoersel.serializer()),
        )

    fun steg2(transaksjonId: UUID): Map<Key, JsonElement> =
        steg1(transaksjonId).plusData(
            Key.VIRKSOMHETER to orgnrMedNavn.toJson(orgMapSerializer),
        )

    fun steg3(transaksjonId: UUID): Map<Key, JsonElement> =
        steg2(transaksjonId).plusData(
            Key.PERSONER to personer.toJson(personMapSerializer),
        )

    fun steg4(transaksjonId: UUID): Map<Key, JsonElement> =
        steg3(transaksjonId).plusData(
            mapOf(
                Key.ER_DUPLIKAT_IM to false.toJson(Boolean.serializer()),
                Key.INNTEKTSMELDING to mockInntektsmeldingV1().toJson(Inntektsmelding.serializer()),
                Key.BESTEMMENDE_FRAVAERSDAG to 20.oktober.toJson(),
            ),
        )
}
