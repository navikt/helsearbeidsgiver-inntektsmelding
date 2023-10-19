package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import io.mockk.coEvery
import io.mockk.mockk
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
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class PersisterImLoeserTest {

    private val rapid = TestRapid()
    private var løser: PersisterImLoeser
    private val repository = mockk<InntektsmeldingRepository>()

    init {
        løser = PersisterImLoeser(rapid, repository)
    }

    @BeforeEach
    fun setup() {
        rapid.reset()
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

        rapid.sendJson(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.BEHOV to BehovType.PERSISTER_IM.toJson(),
            DataFelt.VIRKSOMHET to "Test Virksomhet".toJson(),
            DataFelt.ARBEIDSGIVER_INFORMASJON to PersonDato("Gudrun Arbeidsgiver", null, "").toJson(PersonDato.serializer()),
            DataFelt.ARBEIDSTAKER_INFORMASJON to PersonDato("Toril Arbeidstaker", null, "").toJson(PersonDato.serializer()),
            Key.UUID to UUID.randomUUID().toJson(),
            DataFelt.INNTEKTSMELDING to request.toJson(Innsending.serializer())
        )

        val message = rapid.inspektør.message(0)
        Assertions.assertEquals(EventName.INSENDING_STARTED.name, message.path(Key.EVENT_NAME.str).asText())
        Assertions.assertNotNull(message.path(DataFelt.INNTEKTSMELDING.str).asText())
    }
}
