package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import io.mockk.clearAllMocks
import io.mockk.every
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.Transaction
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
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
    fun `feil tolkes som feil av service`() {
        val fail = Fail(
            feilmelding = "Opprett oppgave feilet.",
            event = EventName.OPPGAVE_OPPRETT_REQUESTED,
            transaksjonId = UUID.randomUUID(),
            forespoerselId = UUID.randomUUID(),
            utloesendeMelding = mapOf(
                Key.BEHOV.toString() to BehovType.OPPRETT_OPPGAVE.toJson()
            ).toJson()
        )

        val failMap = mapOf<IKey, JsonElement>(
            Key.FAIL to fail.toJson(Fail.serializer()),
            Key.EVENT_NAME to fail.event.toJson(),
            Key.UUID to fail.transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to fail.forespoerselId!!.toJson()
        )

        assertEquals(Transaction.TERMINATE, service.determineTransactionState(failMap))
        assertNotNull(service.toFailOrNull(failMap))
    }

    @Test
    fun `skal publisere to behov`() {
        val transaksjonId = randomUuid()
        val forespoerselId = randomUuid()

        mockStatic(::randomUuid) {
            every { randomUuid() } returns transaksjonId

            rapid.sendJson(
                Key.EVENT_NAME to EventName.OPPGAVE_OPPRETT_REQUESTED.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.ORGNRUNDERENHET to "123456789".toJson()
            )
        }

        rapid.sendJson(
            Key.EVENT_NAME to EventName.OPPGAVE_OPPRETT_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Key.DATA to "".toJson(),
            Key.VIRKSOMHET to "TestBedrift A/S".toJson()
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

            rapid.sendJson(
                Key.EVENT_NAME to EventName.OPPGAVE_OPPRETT_REQUESTED.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.ORGNRUNDERENHET to "123456789".toJson()
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

        rapid.sendJson(
            Key.FAIL to fail.toJson(Fail.serializer()),
            Key.EVENT_NAME to fail.event.toJson(),
            Key.UUID to fail.transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to fail.forespoerselId!!.toJson()
        )

        val behov = rapid.inspektør.message(0)
        assertEquals(BehovType.VIRKSOMHET.name, behov.path(Key.BEHOV.str).asText())
        val behov2 = rapid.inspektør.message(1)
        assertEquals(BehovType.OPPRETT_OPPGAVE.name, behov2[Key.BEHOV.str].asText())
        assertEquals("Arbeidsgiver", behov2[Key.VIRKSOMHET.str].asText())
    }
}
