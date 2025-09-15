package no.nav.helsearbeidsgiver.inntektsmelding.innsending.api

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verifySequence
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Forespoersel
import no.nav.hag.simba.utils.felles.json.inntektMapSerializer
import no.nav.hag.simba.utils.felles.json.lesOrNull
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.test.json.lesBehov
import no.nav.hag.simba.utils.felles.test.json.plusData
import no.nav.hag.simba.utils.felles.test.mock.mockFail
import no.nav.hag.simba.utils.felles.test.mock.mockForespoersel
import no.nav.hag.simba.utils.felles.test.mock.mockInnsending
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.rr.service.ServiceRiverStateless
import no.nav.hag.simba.utils.rr.test.message
import no.nav.hag.simba.utils.rr.test.mockConnectToRapid
import no.nav.hag.simba.utils.rr.test.sendJson
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Bonus
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.api.Innsending
import no.nav.helsearbeidsgiver.felles.kafka.innsendingtopic.Innsending.toJson
import no.nav.helsearbeidsgiver.felles.kafka.innsendingtopic.toJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.juli
import no.nav.helsearbeidsgiver.utils.test.date.juni
import no.nav.helsearbeidsgiver.utils.test.date.kl
import no.nav.helsearbeidsgiver.utils.test.date.mai
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.UUID
import no.nav.helsearbeidsgiver.felles.kafka.innsendingtopic.Innsending.EventName as InnsendingEventName
import no.nav.helsearbeidsgiver.felles.kafka.innsendingtopic.Innsending.Key as InnsendingKey

class ValiderApiInnsendingServiceTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockKafkaProducer = mockk<KafkaProducer<String, JsonElement>>()
        val testTopic = "test-topic"

        mockConnectToRapid(testRapid) {
            listOf(
                ServiceRiverStateless(
                    ValiderApiInnsendingService(publisher = it, producer = Producer(testTopic, mockKafkaProducer)),
                ),
            )
        }

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("nytt inntektsmeldingskjema valideres OK og sendes videre til api-innsending tjenesten") {
            val kontekstId = UUID.randomUUID()
            testRapid.sendJson(
                Mock.steg0(kontekstId),
            )

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.message(0).lesBehov() shouldBe BehovType.HENT_TRENGER_IM

            testRapid.sendJson(Mock.steg1(kontekstId))

            testRapid.inspektør.size shouldBeExactly 2
            testRapid.message(1).lesBehov() shouldBe BehovType.HENT_INNTEKT

            testRapid.sendJson(Mock.steg2(kontekstId))

            testRapid.inspektør.size shouldBeExactly 3
            testRapid.message(2).toMap().also {
                Key.EVENT_NAME.lesOrNull(EventName.serializer(), it) shouldBe EventName.API_INNSENDING_VALIDERT
                Key.KONTEKST_ID.lesOrNull(UuidSerializer, it) shouldBe kontekstId

                val data = it[Key.DATA]?.toMap().orEmpty()
                Key.INNSENDING.lesOrNull(Innsending.serializer(), data) shouldBe Mock.innsending
                Key.FORESPOERSEL_SVAR.lesOrNull(Forespoersel.serializer(), data) shouldBe Mock.forespoersel
            }
        }

        test("dersom inntekten ikke stemmer overens med a-ordningen, avvises inntektsmeldingen") {
            val kontekstId = UUID.randomUUID()

            testRapid.sendJson(Mock.steg0(kontekstId))

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.message(0).lesBehov() shouldBe BehovType.HENT_TRENGER_IM

            testRapid.sendJson(Mock.steg1(kontekstId))

            testRapid.inspektør.size shouldBeExactly 2
            testRapid.message(1).lesBehov() shouldBe BehovType.HENT_INNTEKT

            val inntektFraAordningen =
                mapOf(
                    mai(2018) to Mock.inntektBeloep.minus(100),
                    juni(2018) to Mock.inntektBeloep.minus(100),
                    juli(2018) to Mock.inntektBeloep.minus(100),
                )

            testRapid.sendJson(
                Mock.steg2(kontekstId).plusData(Key.INNTEKT to inntektFraAordningen.toJson(inntektMapSerializer)),
            )

            testRapid.inspektør.size shouldBeExactly 2
            val forventetNoekkel = Mock.innsending.skjema.forespoerselId
            val forventetRecord =
                ProducerRecord(
                    testTopic,
                    forventetNoekkel.toString(),
                    Mock.avvistMelding(kontekstId).toJson(),
                )

            verifySequence { mockKafkaProducer.send(forventetRecord) }
        }

        test("dersom inntektsmeldingen inneholder en årsak til endring, sendes den rett videre til api-innsending tjenesten") {
            val kontekstId = UUID.randomUUID()
            val inntekt = Inntekt(beloep = Mock.inntektBeloep, inntektsdato = Mock.inntektsDato, naturalytelser = emptyList(), endringAarsaker = listOf(Bonus))
            val innsendingMedEndringAarsak = Mock.innsending.medInntekt(inntekt)

            testRapid.sendJson(
                Mock.steg0(kontekstId).plusData(
                    Key.INNSENDING to innsendingMedEndringAarsak.toJson(Innsending.serializer()),
                ),
            )

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.message(0).lesBehov() shouldBe BehovType.HENT_TRENGER_IM

            testRapid.sendJson(
                Mock.steg1(kontekstId).plusData(
                    Key.INNSENDING to innsendingMedEndringAarsak.toJson(Innsending.serializer()),
                ),
            )

            testRapid.inspektør.size shouldBeExactly 2
            testRapid.message(1).toMap().also {
                Key.EVENT_NAME.lesOrNull(EventName.serializer(), it) shouldBe EventName.API_INNSENDING_VALIDERT
                Key.KONTEKST_ID.lesOrNull(UuidSerializer, it) shouldBe kontekstId

                val data = it[Key.DATA]?.toMap().orEmpty()
                Key.INNSENDING.lesOrNull(Innsending.serializer(), data) shouldBe innsendingMedEndringAarsak
                Key.FORESPOERSEL_SVAR.lesOrNull(Forespoersel.serializer(), data) shouldBe Mock.forespoersel
            }
        }

        test("skal ved feil ikke foreta seg noe (FeilLytter skal plukke opp og rekjøre meldingen som utløste feilen)") {
            val fail =
                mockFail(
                    feilmelding = "Klarer ikke hente inntekt.",
                    eventName = EventName.API_INNSENDING_STARTET,
                    behovType = BehovType.HENT_INNTEKT,
                )

            testRapid.sendJson(fail.tilMelding())

            testRapid.inspektør.size shouldBeExactly 0
        }
    }) {

    companion object {
        fun Innsending.medInntekt(inntekt: Inntekt): Innsending = this.copy(skjema = this.skjema.copy(inntekt = inntekt))
    }

    private object Mock {
        val inntektBeloep = 544.6
        val inntektsDato = 1.januar
        val inntekt = Inntekt(beloep = inntektBeloep, inntektsdato = inntektsDato, naturalytelser = emptyList(), endringAarsaker = emptyList())
        val innsending = mockInnsending().medInntekt(inntekt)
        val forespoersel = mockForespoersel()

        val inntektFraAordningen =
            mapOf(
                mai(2018) to inntektBeloep,
                juni(2018) to inntektBeloep.plus(9),
                juli(2018) to inntektBeloep.minus(9),
            )

        val mottatt = 15.august.kl(12, 0, 0, 0)

        fun steg0(kontekstId: UUID): Map<Key, JsonElement> =
            mapOf(
                Key.EVENT_NAME to EventName.API_INNSENDING_STARTET.toJson(),
                Key.KONTEKST_ID to kontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.INNSENDING to innsending.toJson(Innsending.serializer()),
                        Key.MOTTATT to mottatt.toJson(),
                    ).toJson(),
            )

        fun steg1(kontekstId: UUID): Map<Key, JsonElement> =
            steg0(kontekstId).plusData(
                Key.FORESPOERSEL_SVAR to forespoersel.toJson(Forespoersel.serializer()),
            )

        fun steg2(kontekstId: UUID): Map<Key, JsonElement> =
            steg1(kontekstId).plusData(
                Key.INNTEKT to inntektFraAordningen.toJson(inntektMapSerializer),
            )

        fun avvistMelding(kontekstId: UUID): Map<InnsendingKey, JsonElement> {
            val avvistInntektsmelding =
                AvvistInntektsmelding(
                    inntektsmeldingId = innsending.innsendingId,
                    feilkode = Feilkode.INNTEKT_AVVIKER_FRA_A_ORDNINGEN,
                )
            return mapOf(
                InnsendingKey.EVENT_NAME to InnsendingEventName.AVVIST_INNTEKTSMELDING.toJson(),
                InnsendingKey.KONTEKST_ID to kontekstId.toJson(),
                InnsendingKey.DATA to
                    mapOf(
                        InnsendingKey.AVVIST_INNTEKTSMELDING to avvistInntektsmelding.toJson(AvvistInntektsmelding.serializer()),
                    ).toJson(),
            )
        }
    }
}
