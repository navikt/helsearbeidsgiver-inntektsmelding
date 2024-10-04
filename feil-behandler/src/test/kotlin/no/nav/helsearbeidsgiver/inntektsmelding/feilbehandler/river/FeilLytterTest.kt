package no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.river

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbStatus
import no.nav.hag.utils.bakgrunnsjobb.MockBakgrunnsjobbRepository
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.ModelUtils.toFailOrNull
import no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.prosessor.FeilProsessor
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.time.LocalDateTime
import java.util.UUID

class FeilLytterTest :
    FunSpec({

        val rapid = TestRapid()
        val repository = MockBakgrunnsjobbRepository()

        val handler = FeilLytter(rapid, repository)

        afterTest {
            repository.deleteAll()
        }

        test("skal håndtere gyldige feil med spesifiserte behov") {
            handler.behovSomHaandteres.forEach { handler.behovSkalHaandteres(lagGyldigFeil(it)) shouldBe true }
        }

        test("skal ignorere gyldige feil med visse behov") {
            val ignorerteBehov = BehovType.entries.filterNot { handler.behovSomHaandteres.contains(it) }
            ignorerteBehov.forEach { handler.behovSkalHaandteres(lagGyldigFeil(it)) shouldBe false }
        }

        test("skal ignorere feil uten behov") {
            val uuid = UUID.randomUUID()
            val feil =
                lagGyldigFeil(BehovType.LAGRE_JOURNALPOST_ID).copy(
                    utloesendeMelding =
                        JsonMessage
                            .newMessage(
                                mapOf(
                                    Key.UUID.str to uuid,
                                    Key.FORESPOERSEL_ID.str to uuid,
                                ),
                            ).toJson()
                            .parseJson(),
                )
            handler.behovSkalHaandteres(feil) shouldBe false
        }

        test("skal håndtere feil uten forespørselId") {
            val feil = lagGyldigFeilUtenForespoerselId(BehovType.LAGRE_JOURNALPOST_ID)
            handler.behovSkalHaandteres(feil) shouldBe true
        }

        test("Ny feil med forskjellig behov og samme id skal lagres") {
            val now = LocalDateTime.now()
            rapid.sendTestMessage(lagRapidFeilmelding())
            repository.findByKjoeretidBeforeAndStatusIn(now.plusMinutes(1), setOf(BakgrunnsjobbStatus.OPPRETTET), true).size shouldBe 1
            rapid.sendTestMessage(lagRapidFeilmelding(BehovType.LAGRE_FORESPOERSEL))
            repository.findByKjoeretidBeforeAndStatusIn(now.plusMinutes(1), setOf(BakgrunnsjobbStatus.OPPRETTET), true).size shouldBe 2
        }

        test("Duplikatfeil (samme feil etter rekjøring) skal oppdatere eksisterende feil -> status: FEILET") {
            val now = LocalDateTime.now()
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

        test("Skal sette jobb til STOPPET når maks antall forsøk er overskredet") {
            val now = LocalDateTime.now()
            val feilmelding = lagRapidFeilmelding()
            val feil = toFailOrNull(feilmelding.parseJson().toMap())!!

            repository.save(
                Bakgrunnsjobb(
                    feil.transaksjonId,
                    FeilProsessor.JOB_TYPE,
                    forsoek = 4,
                    maksAntallForsoek = 3,
                    data = feil.utloesendeMelding.toString(),
                ),
            )
            rapid.sendTestMessage(feilmelding)
            repository.findByKjoeretidBeforeAndStatusIn(now.plusMinutes(1), setOf(BakgrunnsjobbStatus.STOPPET), true).size shouldBe 1
        }

        test("Flere feil i en lang verdikjede (ny feil / nytt behov og ny transaksjon etter en OK rekjøring) skal opprette en ny feil") {
            val now = LocalDateTime.now()
            val transaksjonId = UUID.randomUUID()
            val feilmeldingJournalfoer = lagRapidFeilmelding(BehovType.JOURNALFOER, transaksjonId)
            rapid.sendTestMessage(feilmeldingJournalfoer)
            repository.findByKjoeretidBeforeAndStatusIn(now.plusMinutes(1), setOf(BakgrunnsjobbStatus.OPPRETTET), true).size shouldBe 1
            // nå kjører bakgrunnsjobb, plukker opp feilen og rekjører - det går fint, så feilen kommer ikke på nytt.
            // Istedet feiler neste steg - nytt behov fra samme transaksjon
            val feilmeldingLagre = lagRapidFeilmelding(BehovType.LAGRE_JOURNALPOST_ID, transaksjonId)
            rapid.sendTestMessage(feilmeldingLagre)
            // status på gammel jobb blir ikke oppdatert i denne testen..
            repository.findByKjoeretidBeforeAndStatusIn(now.plusMinutes(1), setOf(BakgrunnsjobbStatus.OPPRETTET), true).size shouldBe 2

            val utloesendeMelding = repository.findByKjoeretidBeforeAndStatusIn(now.plusMinutes(1), setOf(BakgrunnsjobbStatus.OPPRETTET), true)[1].data
            val nyTransaksjonId = Key.UUID.les(UuidSerializer, Json.parseToJsonElement(utloesendeMelding).toMap())
            transaksjonId shouldNotBeEqual nyTransaksjonId

            val nyFeilmeldingLagre = lagRapidFeilmelding(BehovType.LAGRE_JOURNALPOST_ID, nyTransaksjonId)
            rapid.sendTestMessage(nyFeilmeldingLagre) // !! ny tx, ikke samme igjen!

            // Bakgrunnsjobben har blitt oppdatert og går til status FEILET..
            repository.findByKjoeretidBeforeAndStatusIn(now.plusMinutes(1), setOf(BakgrunnsjobbStatus.OPPRETTET), true).size shouldBe 1
            repository.findByKjoeretidBeforeAndStatusIn(now.plusMinutes(1), setOf(BakgrunnsjobbStatus.FEILET), true).size shouldBe 1
        }
    })

fun lagRapidFeilmelding(
    behovType: BehovType = BehovType.LAGRE_JOURNALPOST_ID,
    transaksjonId: UUID = UUID.randomUUID(),
): String {
    val eventName = EventName.INNTEKTSMELDING_MOTTATT
    val forespoerselId = UUID.randomUUID()

    return mapOf(
        Key.FAIL to
            Fail(
                feilmelding = "Klarte ikke journalføre",
                event = eventName,
                transaksjonId = transaksjonId,
                forespoerselId = forespoerselId,
                utloesendeMelding =
                    mapOf(
                        Key.EVENT_NAME to eventName.toJson(),
                        Key.BEHOV to behovType.toJson(),
                        Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                        Key.UUID to transaksjonId.toJson(),
                    ).toJson(),
            ).toJson(Fail.serializer()),
    ).toJson()
        .toString()
}

fun lagGyldigFeil(behov: BehovType): Fail {
    val transaksjonId = UUID.randomUUID()
    val forespoerselID = UUID.randomUUID()
    val jsonMessage =
        JsonMessage.newMessage(
            EventName.OPPGAVE_OPPRETT_REQUESTED.name,
            mapOf(
                Key.BEHOV.str to behov,
                Key.UUID.str to transaksjonId,
                Key.FORESPOERSEL_ID.str to forespoerselID,
            ),
        )
    return Fail("Feil", EventName.OPPGAVE_OPPRETT_REQUESTED, transaksjonId, forespoerselID, jsonMessage.toJson().parseJson())
}

fun lagGyldigFeilUtenForespoerselId(behov: BehovType): Fail {
    val transaksjonId = UUID.randomUUID()
    val jsonMessage =
        JsonMessage.newMessage(
            EventName.OPPGAVE_OPPRETT_REQUESTED.name,
            mapOf(
                Key.BEHOV.str to behov,
                Key.UUID.str to transaksjonId,
            ),
        )
    return Fail("Feil", EventName.OPPGAVE_OPPRETT_REQUESTED, transaksjonId, null, jsonMessage.toJson().parseJson())
}
