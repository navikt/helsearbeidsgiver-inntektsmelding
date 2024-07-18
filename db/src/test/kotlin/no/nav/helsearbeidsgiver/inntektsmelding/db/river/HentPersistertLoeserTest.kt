package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.json.readFail
import no.nav.helsearbeidsgiver.felles.test.mock.mockEksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.db.INNTEKTSMELDING_DOKUMENT
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.UUID

class HentPersistertLoeserTest {
    private val rapid = TestRapid()
    private val behov = BehovType.HENT_PERSISTERT_IM.toString()
    private val repository = mockk<InntektsmeldingRepository>()

    init {
        HentPersistertLoeser(rapid, repository)
    }

    @Test
    fun `skal hente ut Inntektsmelding`() {
        coEvery {
            repository.hentNyesteEksternEllerInternInntektsmelding(any())
        } returns Pair(INNTEKTSMELDING_DOKUMENT, null)
        sendMelding(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.BEHOV to behov.toJson(),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
        )
        val melding = rapid.firstMessage().toMap()
        assertTrue(melding.contains(Key.DATA))
        assertTrue(melding.contains(Key.INNTEKTSMELDING_DOKUMENT))
        assertDoesNotThrow {
            val resultJson = Key.INNTEKTSMELDING_DOKUMENT.les(ResultJson.serializer(), melding)
            resultJson.success.shouldNotBeNull().fromJson(Inntektsmelding.serializer())
        }
    }

    @Test
    fun `skal hente ut EksternInntektsmelding`() {
        coEvery {
            repository.hentNyesteEksternEllerInternInntektsmelding(any())
        } returns Pair(null, mockEksternInntektsmelding())
        sendMelding(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.BEHOV to behov.toJson(),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
        )
        val melding = rapid.firstMessage().toMap()
        assertTrue(melding.contains(Key.DATA))
        assertTrue(melding.contains(Key.EKSTERN_INNTEKTSMELDING))
        assertDoesNotThrow {
            val resultJson = Key.EKSTERN_INNTEKTSMELDING.les(ResultJson.serializer(), melding)
            resultJson.success.shouldNotBeNull().fromJson(EksternInntektsmelding.serializer())
        }
    }

    @Test
    fun `skal håndtere feil`() {
        coEvery {
            repository.hentNyesteEksternEllerInternInntektsmelding(any())
        } throws Exception()
        val fail =
            sendMeldingMedFeil(
                Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
                Key.BEHOV to behov.toJson(),
                Key.UUID to UUID.randomUUID().toJson(),
                Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
            )
        assertNotNull(fail.feilmelding)
        assertEquals("Klarte ikke hente persistert inntektsmelding", fail.feilmelding)
    }

    @Test
    fun `Ingen feilmelding dersom im ikke eksisterer`() {
        coEvery {
            repository.hentNyesteEksternEllerInternInntektsmelding(any())
        } returns Pair(null, null)
        sendMelding(
            Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
            Key.BEHOV to behov.toJson(),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
        )
        val melding = rapid.firstMessage().toMap()
        assertTrue(melding.contains(Key.DATA))
        Key.INNTEKTSMELDING_DOKUMENT.les(ResultJson.serializer(), melding) shouldBe ResultJson(success = null)
        Key.EKSTERN_INNTEKTSMELDING.les(ResultJson.serializer(), melding) shouldBe ResultJson(success = null)
    }

    private fun sendMelding(vararg melding: Pair<Key, JsonElement>) {
        rapid.reset()
        rapid.sendJson(*melding.toList().toTypedArray())
    }

    private fun sendMeldingMedFeil(vararg melding: Pair<Key, JsonElement>): Fail {
        rapid.reset()
        rapid.sendJson(*melding.toList().toTypedArray())
        return rapid.firstMessage().readFail()
    }
}
