package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.OpprettOppgaveMedVirksomhetnavn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OpprettOppgaveMedVirksomhetnavnTest {

    private val rapid = TestRapid()
    private val redisStore = MockRedisStore()
    private var løser: OpprettOppgaveMedVirksomhetnavn

    init {
        løser = OpprettOppgaveMedVirksomhetnavn(rapid, redisStore)
    }

    @Test
    fun `skal publisere to behov`() {
        rapid.sendTestMessage(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.FORESPØRSEL_LAGRET,
                    Key.UUID.str to "uuid",
                    DataFelt.ORGNRUNDERENHET.str to "123456789",
                    Key.FORESPOERSEL_ID.str to "987654321"
                )
            ).toJson()
        )
        val generertForespoerselId = redisStore.get("uuid")
        rapid.sendTestMessage(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.FORESPØRSEL_LAGRET,
                    Key.DATA.str to "",
                    DataFelt.ORGNRUNDERENHET.str to "123456789",
                    DataFelt.VIRKSOMHET.str to "TestBedrift A/S",
                    Key.FORESPOERSEL_ID.str to generertForespoerselId!!,
                    Key.UUID.str to generertForespoerselId!!
                )
            ).toJson()
        )
        val behov = rapid.inspektør.message(0)
        assertEquals(BehovType.VIRKSOMHET.name, behov.path(Key.BEHOV.str).asText())
        val behov2 = rapid.inspektør.message(1)
        assertEquals(BehovType.OPPRETT_OPPGAVE.name, behov2[Key.BEHOV.str].asText())
    }
}
