package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import io.mockk.coEvery
import io.mockk.mockk
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.domene.inntektsmelding.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Bonus
import no.nav.helsearbeidsgiver.domene.inntektsmelding.FullLoennIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Refusjon
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PersisterImLoeserTest {

    private val rapid = TestRapid()
    private var løser: PersisterImLoeser
    private val repository = mockk<InntektsmeldingRepository>()

    init {
        løser = PersisterImLoeser(rapid, repository)
    }

    private fun sendMelding(melding: JsonMessage) {
        rapid.reset()
        rapid.sendTestMessage(melding.toJson())
    }

    @Test
    fun `skal publisere event for Inntektsmelding Mottatt`() {
        coEvery {
            repository.lagreInntektsmelding(any(), any())
        } returns Unit

        val request = Innsending(
            "",
            "",
            emptyList(),
            emptyList(),
            emptyList(),
            LocalDate.now(),
            emptyList(),
            Inntekt(
                bekreftet = true,
                500.0,
                Bonus(),
                true
            ),
            FullLoennIArbeidsgiverPerioden(
                true
            ),
            Refusjon(
                true
            ),
            emptyList(),
            AarsakInnsending.NY,
            true
        )

        sendMelding(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INSENDING_STARTED.name,
                    Key.BEHOV.str to BehovType.PERSISTER_IM.name,
                    DataFelt.VIRKSOMHET.str to "Test Virksomhet",
                    DataFelt.ARBEIDSTAKER_INFORMASJON.str to PersonDato("Test person", null, ""),
                    Key.UUID.str to "uuid",
                    DataFelt.INNTEKTSMELDING.str to request
                )
            )
        )
        val message = rapid.inspektør.message(0)
        Assertions.assertEquals(EventName.INSENDING_STARTED.name, message.path(Key.EVENT_NAME.str).asText())
        Assertions.assertNotNull(message.path(DataFelt.INNTEKTSMELDING.str).asText())
    }
}
