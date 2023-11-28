package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import io.mockk.clearAllMocks
import io.mockk.every
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
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
        val fellesDeprecatedFail = Fail(
            eventName = EventName.OPPGAVE_OPPRETT_REQUESTED,
            behov = BehovType.OPPRETT_OPPGAVE,
            feilmelding = "FEIL",
            data = null,
            uuid = UUID.randomUUID().toString(),
            forespørselId = UUID.randomUUID().toString()
        ).toJsonMessage()
        assertEquals(Transaction.TERMINATE, service.determineTransactionState(fellesDeprecatedFail))
        assertTrue(service.isFailMelding(fellesDeprecatedFail))
    }

    @Disabled("Enable om vi begynner å bruke ny Fail-objekt sammen med CompositeEventListener")
    @Test
    fun `feil fra behov tolkes som feil av service`() {
        val failFraBehov = Behov.create(
            event = EventName.OPPGAVE_OPPRETT_REQUESTED,
            behov = BehovType.OPPRETT_OPPGAVE,
            forespoerselId = "987654321"
        ).createFail("Uff")
        assertTrue(service.isFailMelding(failFraBehov.jsonMessage))
    }

    @Disabled("Enable om vi begynner å bruke ny Fail-objekt sammen med CompositeEventListener")
    @Test
    fun `feil fra rapid and rivers-model tolkes som feil av service`() {
        val rapidAndRiverFail = no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail.create(
            behov = BehovType.OPPRETT_OPPGAVE,
            event = EventName.OPPGAVE_OPPRETT_REQUESTED,
            feilmelding = "OpprettOppgave feilet",
            uuid = UUID.randomUUID().toString()
        )
        assertEquals(Transaction.TERMINATE, service.determineTransactionState(rapidAndRiverFail.jsonMessage))
        assertTrue(service.isFailMelding(rapidAndRiverFail.jsonMessage))
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

        val feil = Fail(
            EventName.OPPGAVE_OPPRETT_REQUESTED,
            BehovType.VIRKSOMHET,
            "Klarte ikke hente virksomhet",
            mapOf(
                Key.ORGNRUNDERENHET to "123456789".toJson()
            ),
            transaksjonId.toString(),
            forespoerselId
        ).toJsonMessage().toJson()
        rapid.sendTestMessage(feil)
        val behov = rapid.inspektør.message(0)
        assertEquals(BehovType.VIRKSOMHET.name, behov.path(Key.BEHOV.str).asText())
        val behov2 = rapid.inspektør.message(1)
        assertEquals(BehovType.OPPRETT_OPPGAVE.name, behov2[Key.BEHOV.str].asText())
        assertEquals("Arbeidsgiver", behov2[Key.VIRKSOMHET.str].asText())
    }
}
