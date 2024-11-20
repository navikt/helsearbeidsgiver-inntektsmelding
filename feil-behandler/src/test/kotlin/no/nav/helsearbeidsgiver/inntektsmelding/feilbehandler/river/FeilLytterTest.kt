package no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.river

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.builtins.serializer
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbStatus
import no.nav.hag.utils.bakgrunnsjobb.MockBakgrunnsjobbRepository
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
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

        FeilLytter(rapid, repository)

        afterTest {
            repository.deleteAll()
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

        test("ved flere feil på samme transaksjon-ID og event, men ulikt innhold, så lagres to jobber med ulik transaksjon-ID") {
            val omEttMinutt = LocalDateTime.now().plusMinutes(1)
            val transaksjonId = UUID.randomUUID()
            val forespoerselMottattFail = lagFail(EventName.FORESPOERSEL_MOTTATT, transaksjonId)
            val forespoerselMottattFailMedUliktInnhold =
                lagFail(EventName.FORESPOERSEL_MOTTATT, transaksjonId).let {
                    it.copy(
                        utloesendeMelding =
                            it.utloesendeMelding
                                .toMap()
                                .plus(Key.ER_DUPLIKAT_IM to "kanskje".toJson())
                                .toJson(),
                    )
                }

            rapid.sendJson(forespoerselMottattFail.tilMelding())

            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.FEILET), true).size shouldBeExactly 0
            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.OPPRETTET), true).size shouldBeExactly 1

            rapid.sendJson(forespoerselMottattFailMedUliktInnhold.tilMelding())

            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.FEILET), true).size shouldBeExactly 0
            val jobber = repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.OPPRETTET), true)

            jobber.size shouldBeExactly 2

            jobber[0].uuid shouldBe transaksjonId
            jobber[0].data.parseJson().toMap() shouldContainExactly forespoerselMottattFail.utloesendeMelding.toMap()

            jobber[1].uuid shouldNotBe transaksjonId
            jobber[1].data.parseJson().toMap().also {
                it[Key.EVENT_NAME]?.fromJson(EventName.serializer()) shouldBe forespoerselMottattFailMedUliktInnhold.event
                it[Key.ER_DUPLIKAT_IM]?.fromJson(String.serializer()) shouldBe "kanskje"

                it[Key.KONTEKST_ID]?.fromJson(UuidSerializer) shouldNotBe transaksjonId
            }
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
                it[Key.KONTEKST_ID]?.fromJson(UuidSerializer) shouldNotBe transaksjonId
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

        test("setter jobb til STOPPET når maks antall forsøk er overskredet") {
            val omEttMinutt = LocalDateTime.now().plusMinutes(1)
            val feil = lagFail(EventName.INNTEKTSMELDING_JOURNALFOERT)

            repository.save(
                Bakgrunnsjobb(
                    feil.transaksjonId,
                    FeilProsessor.JOB_TYPE,
                    forsoek = 4,
                    maksAntallForsoek = 3,
                    data = feil.utloesendeMelding.toString(),
                ),
            )

            rapid.sendJson(feil.tilMelding())

            val stoppede = repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.STOPPET), true)

            stoppede.size shouldBeExactly 1
        }

        context("ignorerer melding") {
            test("med event som ikke støttes") {
                val omEttMinutt = LocalDateTime.now().plusMinutes(1)
                val ikkeStoettetFail = lagFail(EventName.TILGANG_ORG_REQUESTED)

                rapid.sendJson(ikkeStoettetFail.tilMelding())

                val jobber = repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, BakgrunnsjobbStatus.entries.toSet(), true)

                jobber.size shouldBeExactly 0
            }

            test("uten event") {
                val omEttMinutt = LocalDateTime.now().plusMinutes(1)
                val stoettetFailUtenEvent =
                    lagFail(EventName.INNTEKTSMELDING_JOURNALFOERT).let {
                        it.copy(
                            utloesendeMelding =
                                it.utloesendeMelding
                                    .toMap()
                                    .minus(Key.EVENT_NAME)
                                    .toJson(),
                        )
                    }

                rapid.sendJson(stoettetFailUtenEvent.tilMelding())

                val jobber = repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, BakgrunnsjobbStatus.entries.toSet(), true)

                jobber.size shouldBeExactly 0
            }

            test("med event som støttes, men med ugyldig feil") {
                val omEttMinutt = LocalDateTime.now().plusMinutes(1)
                val stoettetFail = lagFail(EventName.INNTEKTSMELDING_JOURNALFOERT)

                rapid.sendJson(
                    stoettetFail.tilMelding().plus(Key.FAIL to "ikke en fail".toJson()),
                )

                val jobber = repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, BakgrunnsjobbStatus.entries.toSet(), true)

                jobber.size shouldBeExactly 0
            }

            test("med event som støttes, men uten feil") {
                val omEttMinutt = LocalDateTime.now().plusMinutes(1)
                val stoettetFail = lagFail(EventName.INNTEKTSMELDING_JOURNALFOERT)

                rapid.sendJson(
                    stoettetFail.tilMelding().minus(Key.FAIL),
                )

                val jobber = repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, BakgrunnsjobbStatus.entries.toSet(), true)

                jobber.size shouldBeExactly 0
            }
        }
    })

private fun lagFail(
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
                Key.KONTEKST_ID to transaksjonId.toJson(),
            ).toJson(),
    )
