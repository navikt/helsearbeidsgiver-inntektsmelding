package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import io.mockk.clearAllMocks
import io.mockk.every
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toJsonNode
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.UUID

class OpprettOppgaveServiceTest {

    private val rapid = TestRapid()
    private val mockRedis = MockRedis()

    private val service = OpprettOppgaveService(rapid, mockRedis.store)

    @BeforeEach
    fun setup() {
        rapid.reset()
        clearAllMocks()
        mockRedis.setup()
    }

    @Test
    fun `erFeilMelding`() {
        val fail = Fail(
            feilmelding = "FEIL",
            event = EventName.OPPGAVE_OPPRETT_REQUESTED,
            transaksjonId = UUID.randomUUID(),
            forespoerselId = UUID.randomUUID(),
            utloesendeMelding = mapOf(
                Key.BEHOV.toString() to BehovType.OPPRETT_OPPGAVE.toJson()
            ).toJson()
        )

        val failJson = JsonMessage.newMessage(
            mapOf(
                Key.FAIL.str to fail.toJson(Fail.serializer()).toJsonNode()
            )
        )

        assertEquals(Transaction.TERMINATE, service.determineTransactionState(failJson))
        assertTrue(service.isFailMelding(failJson))
    }

    @Disabled("Enable om vi begynner å bruke ny Fail-objekt sammen med CompositeEventListener")
    @Test
    fun `feil fra behov tolkes som feil av service`() {
        val failFraBehov = Behov.create(
            event = EventName.OPPGAVE_OPPRETT_REQUESTED,
            behov = BehovType.OPPRETT_OPPGAVE,
            forespoerselId = "987654321"
        ).createFail("Uff")

        val failJson = JsonMessage.newMessage(
            mapOf(
                Key.FAIL.str to failFraBehov.toJson(Fail.serializer()).toJsonNode()
            )
        )

        assertTrue(service.isFailMelding(failJson))
    }

    @Disabled("Enable om vi begynner å bruke ny Fail-objekt sammen med CompositeEventListener")
    @Test
    fun `feil fra rapid and rivers-model tolkes som feil av service`() {
        val rapidAndRiverFail = Fail(
            feilmelding = "OpprettOppgave feilet",
            event = EventName.OPPGAVE_OPPRETT_REQUESTED,
            transaksjonId = UUID.randomUUID(),
            forespoerselId = UUID.randomUUID(),
            utloesendeMelding = mapOf(
                Key.BEHOV.toString() to BehovType.OPPRETT_OPPGAVE.toJson()
            ).toJson()
        )

        val failJson = JsonMessage.newMessage(
            mapOf(
                Key.FAIL.str to rapidAndRiverFail.toJson(Fail.serializer()).toJsonNode()
            )
        )

        assertEquals(Transaction.TERMINATE, service.determineTransactionState(failJson))
        assertTrue(service.isFailMelding(failJson))
    }

    @Test
    fun `skal publisere to behov`() {
        val transaksjonId = randomUuid()
        val forespoerselId = "987654321"

        mockStatic(::randomUuid) {
            every { randomUuid() } returns transaksjonId

            rapid.sendTestMessage(
                JsonMessage.newMessage(
                    mapOf(
                        Key.EVENT_NAME.str to EventName.OPPGAVE_OPPRETT_REQUESTED,
                        Key.UUID.str to UUID.randomUUID(),
                        Key.ORGNRUNDERENHET.str to "123456789",
                        Key.FORESPOERSEL_ID.str to forespoerselId
                    )
                ).toJson()
            )
        }

        rapid.sendTestMessage(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.OPPGAVE_OPPRETT_REQUESTED,
                    Key.DATA.str to "",
                    Key.VIRKSOMHET.str to "TestBedrift A/S",
                    Key.FORESPOERSEL_ID.str to forespoerselId,
                    Key.UUID.str to transaksjonId
                )
            ).toJson()
        )
        val behov = rapid.inspektør.message(0)
        assertEquals(BehovType.VIRKSOMHET.name, behov.path(Key.BEHOV.str).asText())
        val behov2 = rapid.inspektør.message(1)
        assertEquals(BehovType.OPPRETT_OPPGAVE.name, behov2[Key.BEHOV.str].asText())
    }

    @Test
    fun `skal fortsette selv om vi mottar feil fra hent virksomhetnavn`() {
        val transaksjonId = randomUuid()
        val forespoerselId = randomUuid()

        mockStatic(::randomUuid) {
            every { randomUuid() } returns transaksjonId

            rapid.sendTestMessage(
                JsonMessage.newMessage(
                    mapOf(
                        Key.EVENT_NAME.str to EventName.OPPGAVE_OPPRETT_REQUESTED,
                        Key.UUID.str to UUID.randomUUID(),
                        Key.ORGNRUNDERENHET.str to "123456789",
                        Key.FORESPOERSEL_ID.str to forespoerselId
                    )
                ).toJson()
            )
        }

        val fail = Fail(
            feilmelding = "Klarte ikke hente virksomhet",
            event = EventName.OPPGAVE_OPPRETT_REQUESTED,
            transaksjonId = transaksjonId,
            forespoerselId = forespoerselId,
            utloesendeMelding = mapOf(
                Key.BEHOV.toString() to BehovType.VIRKSOMHET.toJson(),
                Key.ORGNRUNDERENHET.toString() to "123456789".toJson()
            ).toJson()
        )

        val failJson = mapOf(
            Key.FAIL.str to fail.toJson(Fail.serializer())
        )
            .toJson()
            .toString()

        rapid.sendTestMessage(failJson)
        val behov = rapid.inspektør.message(0)
        assertEquals(BehovType.VIRKSOMHET.name, behov.path(Key.BEHOV.str).asText())
        val behov2 = rapid.inspektør.message(1)
        assertEquals(BehovType.OPPRETT_OPPGAVE.name, behov2[Key.BEHOV.str].asText())
        assertEquals("Arbeidsgiver", behov2[Key.VIRKSOMHET.str].asText())
    }
}
