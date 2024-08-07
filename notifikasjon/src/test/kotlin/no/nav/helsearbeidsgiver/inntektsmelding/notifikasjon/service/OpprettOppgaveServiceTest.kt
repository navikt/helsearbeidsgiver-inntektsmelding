package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.service

import io.mockk.clearAllMocks
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.orgMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.ModelUtils.toFailOrNull
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class OpprettOppgaveServiceTest {
    private val rapid = TestRapid()

    init {
        ServiceRiverStateless(
            OpprettOppgaveService(rapid),
        ).connect(rapid)
    }

    @BeforeEach
    fun setup() {
        rapid.reset()
        clearAllMocks()
    }

    @Test
    fun `feil tolkes som feil av service`() {
        val fail =
            Fail(
                feilmelding = "Opprett oppgave feilet.",
                event = EventName.OPPGAVE_OPPRETT_REQUESTED,
                transaksjonId = UUID.randomUUID(),
                forespoerselId = UUID.randomUUID(),
                utloesendeMelding =
                    mapOf(
                        Key.BEHOV.toString() to BehovType.OPPRETT_OPPGAVE.toJson(),
                    ).toJson(),
            )

        val failMap =
            mapOf(
                Key.FAIL to fail.toJson(Fail.serializer()),
                Key.EVENT_NAME to fail.event.toJson(),
                Key.UUID to fail.transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to fail.forespoerselId!!.toJson(),
            )

        assertNotNull(toFailOrNull(failMap))
    }

    @Test
    fun `skal publisere to behov`() {
        val transaksjonId = UUID.randomUUID()
        val forespoerselId = UUID.randomUUID()
        val orgnr = Orgnr.genererGyldig()

        rapid.sendJson(
            Key.EVENT_NAME to EventName.OPPGAVE_OPPRETT_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.ORGNRUNDERENHET to orgnr.toJson(),
                ).toJson(),
        )

        rapid.sendJson(
            Key.EVENT_NAME to EventName.OPPGAVE_OPPRETT_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.ORGNRUNDERENHET to orgnr.toJson(),
                    Key.VIRKSOMHETER to
                        mapOf(
                            orgnr to "TestBedrift A/S",
                        ).toJson(orgMapSerializer),
                ).toJson(),
        )
        val behov = rapid.inspektør.message(0)
        assertEquals(BehovType.HENT_VIRKSOMHET_NAVN.name, behov.path(Key.BEHOV.str).asText())
        val behov2 = rapid.inspektør.message(1)
        assertEquals(BehovType.OPPRETT_OPPGAVE.name, behov2[Key.BEHOV.str].asText())
    }

    @Test
    fun `skal fortsette selv om vi mottar feil fra hent virksomhetnavn`() {
        val transaksjonId = UUID.randomUUID()
        val forespoerselId = UUID.randomUUID()
        val orgnr = Orgnr.genererGyldig()

        rapid.sendJson(
            Key.EVENT_NAME to EventName.OPPGAVE_OPPRETT_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.ORGNRUNDERENHET to orgnr.toJson(),
                ).toJson(),
        )

        val fail =
            Fail(
                feilmelding = "Klarte ikke hente virksomhet",
                event = EventName.OPPGAVE_OPPRETT_REQUESTED,
                transaksjonId = transaksjonId,
                forespoerselId = forespoerselId,
                utloesendeMelding =
                    mapOf(
                        Key.EVENT_NAME to EventName.OPPGAVE_OPPRETT_REQUESTED.toJson(),
                        Key.BEHOV to BehovType.HENT_VIRKSOMHET_NAVN.toJson(),
                        Key.UUID to transaksjonId.toJson(),
                        Key.DATA to
                            mapOf(
                                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                                Key.ORGNRUNDERENHET to orgnr.toJson(),
                                Key.ORGNR_UNDERENHETER to setOf(orgnr).toJson(Orgnr.serializer()),
                            ).toJson(),
                    ).toJson(),
            )

        rapid.sendJson(
            Key.FAIL to fail.toJson(Fail.serializer()),
            Key.EVENT_NAME to fail.event.toJson(),
            Key.UUID to fail.transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to fail.forespoerselId!!.toJson(),
        )

        val behov = rapid.inspektør.message(0)
        assertEquals(BehovType.HENT_VIRKSOMHET_NAVN.name, behov.path(Key.BEHOV.str).asText())
        val behov2 = rapid.inspektør.message(1)
        assertEquals(BehovType.OPPRETT_OPPGAVE.name, behov2[Key.BEHOV.str].asText())
        assertEquals("Arbeidsgiver", behov2[Key.VIRKSOMHET.str].asText())
    }
}
