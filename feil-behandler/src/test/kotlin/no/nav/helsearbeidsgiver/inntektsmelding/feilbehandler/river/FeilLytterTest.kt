package no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.river

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.prosessor.FeilProsessor
import no.nav.helsearbeidsgiver.utils.json.fromJson
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
            handler.behovSomHaandteres.forEach { handler.behovSkalHaandteres(utloesendeMelding(it)) shouldBe true }
        }

        test("skal ignorere gyldige feil med visse behov") {
            val ignorerteBehov = BehovType.entries.filterNot { handler.behovSomHaandteres.contains(it) }
            ignorerteBehov.forEach { handler.behovSkalHaandteres(utloesendeMelding(it)) shouldBe false }
        }

        test("skal ignorere feil uten behov") {
            val utloesendeMelding =
                mapOf(
                    Key.UUID to UUID.randomUUID().toJson(),
                    Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
                )
            handler.behovSkalHaandteres(utloesendeMelding) shouldBe false
        }

        test("skal behandle feil uten behov, men med spesifiserte eventer") {
            handler.eventerSomHaandteres.forEach { event ->
                val utloesendeMelding =
                    mapOf(
                        Key.EVENT_NAME to event.toJson(),
                        Key.UUID to UUID.randomUUID().toJson(),
                        Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
                    )

                handler.eventSkalHaandteres(utloesendeMelding).shouldBeTrue()
            }
        }

        test("skal håndtere feil uten forespørselId") {
            val utloesendeMelding = utloesendeMelding(BehovType.JOURNALFOER).minus(Key.FORESPOERSEL_ID)
            handler.behovSkalHaandteres(utloesendeMelding) shouldBe true
        }

        test("Ny feil med forskjellig behov og samme id skal lagres") {
            val now = LocalDateTime.now()
            rapid.sendTestMessage(lagRapidFeilmelding())
            repository.findByKjoeretidBeforeAndStatusIn(now.plusMinutes(1), setOf(BakgrunnsjobbStatus.OPPRETTET), true).size shouldBe 1
            rapid.sendTestMessage(lagRapidFeilmelding(BehovType.JOURNALFOER))
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
            val feil = feilmelding.parseJson().toFailOrNull()!!

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
            val feilmeldingLagre = lagRapidFeilmelding(BehovType.JOURNALFOER, transaksjonId)
            rapid.sendTestMessage(feilmeldingLagre)
            // status på gammel jobb blir ikke oppdatert i denne testen..
            repository.findByKjoeretidBeforeAndStatusIn(now.plusMinutes(1), setOf(BakgrunnsjobbStatus.OPPRETTET), true).size shouldBe 2

            val utloesendeMelding = repository.findByKjoeretidBeforeAndStatusIn(now.plusMinutes(1), setOf(BakgrunnsjobbStatus.OPPRETTET), true)[1].data
            val nyTransaksjonId = Key.UUID.les(UuidSerializer, Json.parseToJsonElement(utloesendeMelding).toMap())
            transaksjonId shouldNotBeEqual nyTransaksjonId

            val nyFeilmeldingLagre = lagRapidFeilmelding(BehovType.JOURNALFOER, nyTransaksjonId)
            rapid.sendTestMessage(nyFeilmeldingLagre) // !! ny tx, ikke samme igjen!

            // Bakgrunnsjobben har blitt oppdatert og går til status FEILET..
            repository.findByKjoeretidBeforeAndStatusIn(now.plusMinutes(1), setOf(BakgrunnsjobbStatus.OPPRETTET), true).size shouldBe 1
            repository.findByKjoeretidBeforeAndStatusIn(now.plusMinutes(1), setOf(BakgrunnsjobbStatus.FEILET), true).size shouldBe 1
        }

        test("ved flere feil på samme transaksjon-ID og event, så oppdateres eksisterende jobb") {
            val omEttMinutt = LocalDateTime.now().plusMinutes(1)
            val forespoerselMottattFail = lagFail(EventName.FORESPOERSEL_MOTTATT)

            rapid.sendJson(forespoerselMottattFail.tilMelding())

            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.FEILET), true).size shouldBeExactly 0
            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.OPPRETTET), true).size shouldBeExactly 1

            rapid.sendJson(forespoerselMottattFail.tilMelding())

            val jobber = repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.FEILET), true)
            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.OPPRETTET), true).size shouldBeExactly 0

            jobber.size shouldBeExactly 1

            jobber[0].uuid shouldBe forespoerselMottattFail.transaksjonId
            jobber[0].data.parseJson().toMap() shouldContainExactly forespoerselMottattFail.utloesendeMelding.toMap()
        }

        test("ved flere feil på samme transaksjon-ID, men ulik event, så lagres to jobber med ulik transaksjon-ID") {
            val omEttMinutt = LocalDateTime.now().plusMinutes(1)
            val transaksjonId = UUID.randomUUID()
            val forespoerselMottattFail = lagFail(EventName.FORESPOERSEL_MOTTATT, transaksjonId)
            val forespoerselBesvartFail = lagFail(EventName.FORESPOERSEL_BESVART, transaksjonId)

            rapid.sendJson(forespoerselMottattFail.tilMelding())

            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.FEILET), true).size shouldBeExactly 0
            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.OPPRETTET), true).size shouldBeExactly 1

            rapid.sendJson(forespoerselBesvartFail.tilMelding())

            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.FEILET), true).size shouldBeExactly 0
            val jobber = repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.OPPRETTET), true)

            jobber.size shouldBeExactly 2

            jobber[0].uuid shouldBe transaksjonId
            jobber[0].data.parseJson().toMap() shouldContainExactly forespoerselMottattFail.utloesendeMelding.toMap()

            jobber[1].uuid shouldNotBe transaksjonId
            jobber[1].data.parseJson().toMap().also {
                it[Key.EVENT_NAME]?.fromJson(EventName.serializer()) shouldBe forespoerselBesvartFail.event
                it[Key.UUID]?.fromJson(UuidSerializer) shouldNotBe transaksjonId
            }
        }

        test("ved flere feil på ulik transaksjon-ID, men samme event, så lagres to jobber (med ulik transaksjon-ID)") {
            val omEttMinutt = LocalDateTime.now().plusMinutes(1)
            val forespoerselMottattFail1 = lagFail(EventName.FORESPOERSEL_MOTTATT)
            val forespoerselMottattFail2 = lagFail(EventName.FORESPOERSEL_MOTTATT)

            rapid.sendJson(forespoerselMottattFail1.tilMelding())

            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.FEILET), true).size shouldBeExactly 0
            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.OPPRETTET), true).size shouldBeExactly 1

            rapid.sendJson(forespoerselMottattFail2.tilMelding())

            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.FEILET), true).size shouldBeExactly 0
            val jobber = repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.OPPRETTET), true)

            jobber.size shouldBeExactly 2

            jobber[0].uuid shouldBe forespoerselMottattFail1.transaksjonId
            jobber[0].data.parseJson().toMap() shouldContainExactly forespoerselMottattFail1.utloesendeMelding.toMap()

            jobber[1].uuid shouldBe forespoerselMottattFail2.transaksjonId
            jobber[1].data.parseJson().toMap() shouldContainExactly forespoerselMottattFail2.utloesendeMelding.toMap()
        }

        test("ved flere feil på ulik transaksjon-ID og ulik event, så lagres to jobber (med ulik transaksjon-ID)") {
            val omEttMinutt = LocalDateTime.now().plusMinutes(1)
            val forespoerselMottattFail = lagFail(EventName.FORESPOERSEL_MOTTATT)
            val forespoerselBesvartFail = lagFail(EventName.FORESPOERSEL_BESVART)

            rapid.sendJson(forespoerselMottattFail.tilMelding())

            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.FEILET), true).size shouldBeExactly 0
            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.OPPRETTET), true).size shouldBeExactly 1

            rapid.sendJson(forespoerselBesvartFail.tilMelding())

            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.FEILET), true).size shouldBeExactly 0
            val jobber = repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.OPPRETTET), true)

            jobber.size shouldBeExactly 2

            jobber[0].uuid shouldBe forespoerselMottattFail.transaksjonId
            jobber[0].data.parseJson().toMap() shouldContainExactly forespoerselMottattFail.utloesendeMelding.toMap()

            jobber[1].uuid shouldBe forespoerselBesvartFail.transaksjonId
            jobber[1].data.parseJson().toMap() shouldContainExactly forespoerselBesvartFail.utloesendeMelding.toMap()
        }
    })

fun lagRapidFeilmelding(
    behovType: BehovType = BehovType.JOURNALFOER,
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

fun lagFail(
    eventName: EventName,
    transaksjonId: UUID = UUID.randomUUID(),
): Fail =
    Fail(
        feilmelding = "skux life",
        event = eventName,
        transaksjonId = transaksjonId,
        forespoerselId = null,
        utloesendeMelding =
            mapOf(
                Key.EVENT_NAME to eventName.toJson(),
                Key.UUID to transaksjonId.toJson(),
            ).toJson(),
    )

fun utloesendeMelding(behov: BehovType): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
        Key.BEHOV to behov.toJson(),
        Key.UUID to UUID.randomUUID().toJson(),
        Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
    )
