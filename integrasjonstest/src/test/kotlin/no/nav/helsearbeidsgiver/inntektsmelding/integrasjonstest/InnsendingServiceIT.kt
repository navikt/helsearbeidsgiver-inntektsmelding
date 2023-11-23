package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.dokarkiv.domene.OpprettOgFerdigstillResponse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Innsending
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.test.mock.GYLDIG_INNSENDING_REQUEST
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InnsendingServiceIT : EndToEndTest() {

    @Test
    fun `Test at innsending er mottatt`() {
        forespoerselRepository.lagreForespoersel(Mock.forespoerselId.toString(), Mock.ORGNR)
        forespoerselRepository.oppdaterSakId(Mock.forespoerselId.toString(), Mock.SAK_ID)
        forespoerselRepository.oppdaterOppgaveId(Mock.forespoerselId.toString(), Mock.OPPGAVE_ID)

        coEvery {
            dokarkivClient.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any())
        } returns OpprettOgFerdigstillResponse(
            journalpostId = "journalpost-id-sukkerspinn",
            journalpostFerdigstilt = true,
            melding = "Ha en brillefin dag!",
            dokumenter = emptyList()
        )

        publish(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.OPPRETTET to LocalDateTime.now().toJson(),
            Key.CLIENT_ID to Mock.clientId.toJson(),
            Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
            DataFelt.ORGNRUNDERENHET to Mock.ORGNR.toJson(),
            Key.IDENTITETSNUMMER to Mock.FNR.toJson(),
            Key.ARBEIDSGIVER_ID to Mock.FNR_AG.toJson(),
            DataFelt.INNTEKTSMELDING to GYLDIG_INNSENDING_REQUEST.toJson(Innsending.serializer())
        )

        Thread.sleep(10000)

        messages.all().filter(Mock.clientId).size shouldBe 10

        messages.filterFeil().all().size shouldBe 0

        val innsendingStr = redisStore.get(RedisKey.of(Mock.clientId)).shouldNotBeNull()
        innsendingStr.length shouldBeGreaterThan 2
    }

    private object Mock {
        const val ORGNR = "stolt-krakk"
        const val FNR = "kongelig-albatross"
        const val FNR_AG = "uutgrunnelig-koffert"
        const val SAK_ID = "tjukk-kalender"
        const val OPPGAVE_ID = "kunstig-demon"

        val forespoerselId = randomUuid()
        val clientId = randomUuid()
    }
}

private fun List<JsonElement>.filter(clientId: UUID): List<JsonElement> {
    var transaksjonId: UUID? = null
    return filter {
        val msg = it.toMap()

        val msgClientId = msg[Key.CLIENT_ID]?.fromJson(UuidSerializer)
        if (msgClientId == clientId) {
            true
        } else {
            val eventName = msg[Key.EVENT_NAME]?.fromJson(EventName.serializer()).shouldNotBeNull()

            if (transaksjonId == null && (eventName == EventName.INSENDING_STARTED && msg.contains(Key.BEHOV))) {
                transaksjonId = msg[Key.UUID]?.fromJson(UuidSerializer)
            }

            val uuid = msg[Key.UUID]?.fromJson(UuidSerializer)

            val innsendingStartetEllerImMottatt = eventName == EventName.INSENDING_STARTED ||
                (eventName == EventName.INNTEKTSMELDING_MOTTATT && !msg.contains(Key.BEHOV))

            uuid != null &&
                uuid == transaksjonId &&
                innsendingStartetEllerImMottatt &&
                !msg.contains(Key.LØSNING)
        }
    }
}
