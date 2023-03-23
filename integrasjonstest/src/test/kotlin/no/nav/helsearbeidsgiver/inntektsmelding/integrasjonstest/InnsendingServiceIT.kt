package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.common.runBlocking
import io.mockk.every
import no.nav.helsearbeidsgiver.aareg.Arbeidsavtale
import no.nav.helsearbeidsgiver.aareg.Arbeidsforhold
import no.nav.helsearbeidsgiver.aareg.Opplysningspliktig
import no.nav.helsearbeidsgiver.aareg.Periode
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InnsendingServiceIT : EndToEndTest() {

    @Test
    fun `Test at innsnending er mottatt`() {
        val uuid = UUID.randomUUID().toString()
        val brregClient = this.brregClient
        every {
            runBlocking {
                brregClient.hentVirksomhetNavn(TestData.validOrgNr)
            }
        } answers {
            "Test Virksomhet Navn"
        }

        every {
            runCatching {
                kotlinx.coroutines.runBlocking {
                    aaregClient.hentArbeidsforhold(TestData.validIdentitetsnummer, any())
                }
            }
        } answers {
            val arbeidsforhold = Arbeidsforhold(
                no.nav.helsearbeidsgiver.aareg.Arbeidsgiver(type = "Underenhet", TestData.validOrgNr),
                Opplysningspliktig("Underenhet", TestData.validOrgNr),
                listOf(Arbeidsavtale(100.toDouble(), Periode(LocalDate.parse("2022-01-19"), LocalDate.parse("2022-12-12")))),
                no.nav.helsearbeidsgiver.aareg.Ansettelsesperiode(Periode(LocalDate.parse("2022-02-10"), LocalDate.parse("2022-04-17"))),
                LocalDateTime.now()
            )
            Result.success(listOf(arbeidsforhold))
        }

        publish(
            mapOf(
                Key.EVENT_NAME.str to EventName.INSENDING_STARTED.name,
                Key.UUID.str to uuid,
                Key.INNTEKTSMELDING.str to GYLDIG,
                Key.ORGNRUNDERENHET.str to TestData.validOrgNr,
                Key.IDENTITETSNUMMER.str to TestData.validIdentitetsnummer
            )
        )
        Thread.sleep(20000)
        assert(getMessageCount() == 9)
    }
}
