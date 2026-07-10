package no.nav.helsearbeidsgiver.inntektsmelding.innsending.ekstern

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verifySequence
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.domene.forespoersel.test.mockForespoersel
import no.nav.hag.simba.kontrakt.kafkatopic.innsending.Innsending.toJson
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.lesOrNull
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.test.json.lesBehov
import no.nav.hag.simba.utils.felles.test.json.plusData
import no.nav.hag.simba.utils.felles.test.mock.mockFail
import no.nav.hag.simba.utils.felles.test.mock.mockInnsending
import no.nav.hag.simba.utils.kafka.Producer
import no.nav.hag.simba.utils.rr.service.ServiceRiverStateless
import no.nav.hag.simba.utils.rr.test.message
import no.nav.hag.simba.utils.rr.test.mockConnectToRapid
import no.nav.hag.simba.utils.rr.test.sendJson
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.api.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.kl
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.UUID
import no.nav.hag.simba.kontrakt.kafkatopic.innsending.Innsending.EventName as InnsendingEventName
import no.nav.hag.simba.kontrakt.kafkatopic.innsending.Innsending.Key as InnsendingKey

class ApiInnsendingServiceTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockKafkaProducer = mockk<KafkaProducer<String, JsonElement>>()
        val testTopic = "test-topic"

        mockConnectToRapid(testRapid) {
            listOf(
                ServiceRiverStateless(
                    ApiInnsendingService(it, Producer(testTopic, mockKafkaProducer)),
                ),
            )
        }

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("nytt inntektsmeldingskjema lagres og sendes videre til beriking") {
            val kontekstId = UUID.randomUUID()
            val innsending = Mock.innsending
            testRapid.sendJson(
                Mock.steg0(kontekstId),
            )

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.message(0).lesBehov() shouldBe BehovType.LAGRE_IM_SKJEMA

            testRapid.sendJson(Mock.steg1(kontekstId))

            testRapid.inspektør.size shouldBeExactly 2
            testRapid.message(1).toMap().also {
                Key.EVENT_NAME.lesOrNull(EventName.serializer(), it) shouldBe EventName.INNTEKTSMELDING_SKJEMA_LAGRET
                Key.KONTEKST_ID.lesOrNull(UuidSerializer, it) shouldBe kontekstId

                val data = it[Key.DATA]?.toMap().orEmpty()
                Key.SKJEMA_INNTEKTSMELDING.lesOrNull(SkjemaInntektsmelding.serializer(), data) shouldBe innsending.skjema
            }
        }

        test("duplikat skjema sendes _ikke_ videre til beriking, men sendes til api-innsending-topicet som avvist") {
            val kontekstId = UUID.randomUUID()

            testRapid.sendJson(Mock.steg0(kontekstId))

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.message(0).lesBehov() shouldBe BehovType.LAGRE_IM_SKJEMA

            testRapid.sendJson(
                Mock.steg1(kontekstId).plusData(
                    Key.ER_DUPLIKAT_IM to true.toJson(Boolean.serializer()),
                ),
            )
            testRapid.inspektør.size shouldBeExactly 1

            val forventetNoekkel = Mock.innsending.skjema.forespoerselId
            val forventetRecord =
                ProducerRecord(
                    testTopic,
                    forventetNoekkel.toString(),
                    Mock.mockAvvistDuplikatMelding(kontekstId).toJson(),
                )

            verifySequence { mockKafkaProducer.send(forventetRecord) }
        }

        test("svar med feilmelding ved feil") {
            val fail =
                mockFail(
                    feilmelding = "Databasen er smekk full.",
                    eventName = EventName.API_INNSENDING_VALIDERT,
                    behovType = BehovType.HENT_TRENGER_IM,
                )

            testRapid.sendJson(Mock.steg0(fail.kontekstId))

            testRapid.sendJson(fail.tilMelding())

            testRapid.inspektør.size shouldBeExactly 1
        }
    })

private object Mock {
    val innsending = mockInnsending()
    val mottatt = 15.august.kl(12, 0, 0, 0)
    val forespoersel = mockForespoersel()

    fun steg0(kontekstId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.API_INNSENDING_VALIDERT.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.INNSENDING to innsending.toJson(Innsending.serializer()),
                    Key.MOTTATT to mottatt.toJson(),
                    Key.FORESPOERSEL_SVAR to forespoersel.toJson(),
                ).toJson(),
        )

    fun steg1(kontekstId: UUID): Map<Key, JsonElement> =
        steg0(kontekstId)
            .plus(Key.EVENT_NAME to EventName.SERVICE_EKSTERN_IM_LAGRE_SKJEMA.toJson())
            .plusData(
                mapOf(
                    Key.SKJEMA_INNTEKTSMELDING to innsending.skjema.toJson(SkjemaInntektsmelding.serializer()),
                    Key.INNTEKTSMELDING_ID to innsending.innsendingId.toJson(),
                    Key.ER_DUPLIKAT_IM to false.toJson(Boolean.serializer()),
                ),
            )

    fun mockAvvistDuplikatMelding(kontekstId: UUID): Map<InnsendingKey, JsonElement> {
        val avvistInntektsmelding =
            AvvistInntektsmelding(
                inntektsmeldingId = innsending.innsendingId,
                forespoerselId = innsending.type.id,
                vedtaksperiodeId = forespoersel.vedtaksperiodeId,
                orgnr = forespoersel.orgnr,
                feil = Feil(Feilkode.DUPLIKAT, "Duplikat"),
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
