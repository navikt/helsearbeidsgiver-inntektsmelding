package no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.river

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbStatus
import no.nav.hag.utils.bakgrunnsjobb.MockBakgrunnsjobbRepository
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.utils.json.parseJson
import java.time.LocalDateTime
import java.util.UUID

class FeilLytterTest : FunSpec({

    val rapid = TestRapid()
    val repository = MockBakgrunnsjobbRepository()

    val handler = FeilLytter(rapid, repository)

    test("skal håndtere gyldige feil med spesifiserte behov") {
        handler.behovSomHaandteres.forEach { handler.skalHaandteres(lagGyldigFeil(it)) shouldBe true }
    }

    test("skal ignorere gyldige feil med visse behov") {
        val ignorerteBehov = BehovType.entries.filterNot { handler.behovSomHaandteres.contains(it) }
        ignorerteBehov.forEach { handler.skalHaandteres(lagGyldigFeil(it)) shouldBe false }
    }

    test("skal ignorere feil uten behov") {
        val uuid = UUID.randomUUID()
        val feil = lagGyldigFeil(BehovType.JOURNALFOER).copy(
            utloesendeMelding =
            JsonMessage.newMessage(
                mapOf(
                    Key.UUID.str to uuid,
                    Key.FORESPOERSEL_ID.str to uuid
                )
            ).toJson().parseJson()
        )
        handler.skalHaandteres(feil) shouldBe false
    }

    test("skal ignorere feil uten forespørselId") {
        // TODO: Kan egentlig tillate feil uten forespørselId..
        val feil = lagGyldigFeil(BehovType.JOURNALFOER).copy(forespoerselId = null)
        handler.skalHaandteres(feil) shouldBe false
    }

    test("Ny feil med forskjellig behov og samme id skal lagres, gjentakende feil oppdaterer jobb") {
        val now = LocalDateTime.now()
        repository.deleteAll()
        rapid.sendTestMessage(lagRapidFeilmelding())
        repository.findByKjoeretidBeforeAndStatusIn(now.plusMinutes(1), setOf(BakgrunnsjobbStatus.OPPRETTET), true).size shouldBe 1
        rapid.sendTestMessage(lagRapidFeilmelding("LAGRE_FORESPOERSEL"))
        repository.findByKjoeretidBeforeAndStatusIn(now.plusMinutes(1), setOf(BakgrunnsjobbStatus.OPPRETTET), true).size shouldBe 2
    }

    test("Duplikatfeil (samme feil etter rekjøring) skal oppdatere eksisterende feil -> status: FEILET") {
        val now = LocalDateTime.now()
        repository.deleteAll()
        val feilmelding = lagRapidFeilmelding()
        rapid.sendTestMessage(feilmelding)
        repository.findByKjoeretidBeforeAndStatusIn(now.plusMinutes(1), setOf(BakgrunnsjobbStatus.OPPRETTET), true).size shouldBe 1
        rapid.sendTestMessage(feilmelding)
        repository.findByKjoeretidBeforeAndStatusIn(now.plusMinutes(1), setOf(BakgrunnsjobbStatus.OPPRETTET), true).size shouldBe 0
        val oppdatert = repository.findByKjoeretidBeforeAndStatusIn(now.plusMinutes(1), setOf(BakgrunnsjobbStatus.FEILET), true)
        oppdatert.size shouldBe 1
        oppdatert[0].forsoek shouldBe 0 // Antall forsøk oppdateres av bakgrunnsjobbService

        rapid.sendTestMessage(feilmelding)
        rapid.sendTestMessage(feilmelding)
        rapid.sendTestMessage(feilmelding)
        val feilet = repository.findByKjoeretidBeforeAndStatusIn(now.plusMinutes(1), setOf(BakgrunnsjobbStatus.FEILET), true)
        feilet.size shouldBe 1
        feilet[0].forsoek shouldBe 0
    }
})
fun lagRapidFeilmelding(behov: String = "JOURNALFOER"): String {
    return """
        {   "fail": {
                "feilmelding": "Klarte ikke journalføre",
                "event": "INNTEKTSMELDING_MOTTATT",
                "transaksjonId": "96fe8a6b-6667-4a7b-ad20-f5ed829eccaf",
                "forespoerselId": "ec50627c-26d8-44c9-866c-e42f46b5890b",
                "utloesendeMelding": {
                    "@event_name": "INNTEKTSMELDING_MOTTATT",
                    "@behov": "$behov",
                    "forespoerselId": "ec50627c-26d8-44c9-866c-e42f46b5890b",
                    "uuid": "96fe8a6b-6667-4a7b-ad20-f5ed829eccaf"
                }
            }
        }
    """.trimIndent()
}
fun lagGyldigFeil(behov: BehovType): Fail {
    val uuid = UUID.randomUUID()
    val jsonMessage = JsonMessage.newMessage(
        EventName.OPPGAVE_OPPRETT_REQUESTED.name,
        mapOf(
            Key.BEHOV.str to behov,
            Key.UUID.str to uuid,
            Key.FORESPOERSEL_ID.str to uuid
        )
    )
    return Fail("Feil", EventName.OPPGAVE_OPPRETT_REQUESTED, UUID.randomUUID(), UUID.randomUUID(), jsonMessage.toJson().parseJson())
}
