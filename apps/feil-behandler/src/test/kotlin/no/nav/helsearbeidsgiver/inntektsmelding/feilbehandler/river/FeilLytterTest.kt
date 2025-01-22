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
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
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

        FeilLytter(repository).connect(rapid)

        afterTest {
            repository.deleteAll()
        }

        test("ved flere feil på samme kontekst-ID og event, så oppdateres eksisterende jobb") {
            val omEttMinutt = LocalDateTime.now().plusMinutes(1)
            val forespoerselMottattFail = mockFail("skux life", EventName.FORESPOERSEL_MOTTATT)

            rapid.sendJson(forespoerselMottattFail.tilMelding())

            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.FEILET), true).size shouldBeExactly 0
            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.OPPRETTET), true).size shouldBeExactly 1

            rapid.sendJson(forespoerselMottattFail.tilMelding())

            val jobber = repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.FEILET), true)
            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.OPPRETTET), true).size shouldBeExactly 0

            jobber.size shouldBeExactly 1

            jobber[0].uuid shouldBe forespoerselMottattFail.kontekstId
            jobber[0].data.parseJson().toMap() shouldContainExactly forespoerselMottattFail.utloesendeMelding
        }

        test("ved flere feil på samme kontekst-ID og event, men ulikt innhold, så lagres to jobber med ulik kontekst-ID") {
            val omEttMinutt = LocalDateTime.now().plusMinutes(1)
            val kontekstId = UUID.randomUUID()
            val forespoerselMottattFail = mockFail("tux life", EventName.FORESPOERSEL_MOTTATT, kontekstId)
            val forespoerselMottattFailMedUliktInnhold =
                mockFail("hux life", EventName.FORESPOERSEL_MOTTATT, kontekstId).let {
                    it.copy(
                        utloesendeMelding =
                            it.utloesendeMelding.plus(
                                Key.ER_DUPLIKAT_IM to "kanskje".toJson(),
                            ),
                    )
                }

            rapid.sendJson(forespoerselMottattFail.tilMelding())

            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.FEILET), true).size shouldBeExactly 0
            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.OPPRETTET), true).size shouldBeExactly 1

            rapid.sendJson(forespoerselMottattFailMedUliktInnhold.tilMelding())

            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.FEILET), true).size shouldBeExactly 0
            val jobber = repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.OPPRETTET), true)

            jobber.size shouldBeExactly 2

            jobber[0].uuid shouldBe kontekstId
            jobber[0].data.parseJson().toMap() shouldContainExactly forespoerselMottattFail.utloesendeMelding

            jobber[1].uuid shouldNotBe kontekstId
            jobber[1].data.parseJson().toMap().also {
                it[Key.EVENT_NAME]?.fromJson(EventName.serializer()) shouldBe
                    forespoerselMottattFailMedUliktInnhold.utloesendeMelding[Key.EVENT_NAME]?.fromJson(EventName.serializer())
                it[Key.ER_DUPLIKAT_IM]?.fromJson(String.serializer()) shouldBe "kanskje"

                it[Key.KONTEKST_ID]?.fromJson(UuidSerializer) shouldNotBe kontekstId
            }
        }

        test("ved flere feil på samme kontekst-ID, men ulik event, så lagres to jobber med ulik kontekst-ID") {
            val omEttMinutt = LocalDateTime.now().plusMinutes(1)
            val kontekstId = UUID.randomUUID()
            val forespoerselMottattFail = mockFail("fox life", EventName.FORESPOERSEL_MOTTATT, kontekstId)
            val forespoerselBesvartFail = mockFail("nox life", EventName.FORESPOERSEL_BESVART, kontekstId)

            rapid.sendJson(forespoerselMottattFail.tilMelding())

            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.FEILET), true).size shouldBeExactly 0
            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.OPPRETTET), true).size shouldBeExactly 1

            rapid.sendJson(forespoerselBesvartFail.tilMelding())

            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.FEILET), true).size shouldBeExactly 0
            val jobber = repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.OPPRETTET), true)

            jobber.size shouldBeExactly 2

            jobber[0].uuid shouldBe kontekstId
            jobber[0].data.parseJson().toMap() shouldContainExactly forespoerselMottattFail.utloesendeMelding

            jobber[1].uuid shouldNotBe kontekstId
            jobber[1].data.parseJson().toMap().also {
                it[Key.EVENT_NAME]?.fromJson(EventName.serializer()) shouldBe
                    forespoerselBesvartFail.utloesendeMelding[Key.EVENT_NAME]?.fromJson(EventName.serializer())
                it[Key.KONTEKST_ID]?.fromJson(UuidSerializer) shouldNotBe kontekstId
            }
        }

        test("ved flere feil på ulik kontekst-ID, men samme event, så lagres to jobber (med ulik kontekst-ID)") {
            val omEttMinutt = LocalDateTime.now().plusMinutes(1)
            val forespoerselMottattFail1 = mockFail("ux life", EventName.FORESPOERSEL_MOTTATT)
            val forespoerselMottattFail2 = mockFail("ux life", EventName.FORESPOERSEL_MOTTATT)

            rapid.sendJson(forespoerselMottattFail1.tilMelding())

            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.FEILET), true).size shouldBeExactly 0
            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.OPPRETTET), true).size shouldBeExactly 1

            rapid.sendJson(forespoerselMottattFail2.tilMelding())

            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.FEILET), true).size shouldBeExactly 0
            val jobber = repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.OPPRETTET), true)

            jobber.size shouldBeExactly 2

            jobber[0].uuid shouldBe forespoerselMottattFail1.kontekstId
            jobber[0].data.parseJson().toMap() shouldContainExactly forespoerselMottattFail1.utloesendeMelding

            jobber[1].uuid shouldBe forespoerselMottattFail2.kontekstId
            jobber[1].data.parseJson().toMap() shouldContainExactly forespoerselMottattFail2.utloesendeMelding
        }

        test("ved flere feil på ulik kontekst-ID og ulik event, så lagres to jobber (med ulik kontekst-ID)") {
            val omEttMinutt = LocalDateTime.now().plusMinutes(1)
            val forespoerselMottattFail = mockFail("lux life", EventName.FORESPOERSEL_MOTTATT)
            val forespoerselBesvartFail = mockFail("rux life", EventName.FORESPOERSEL_BESVART)

            rapid.sendJson(forespoerselMottattFail.tilMelding())

            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.FEILET), true).size shouldBeExactly 0
            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.OPPRETTET), true).size shouldBeExactly 1

            rapid.sendJson(forespoerselBesvartFail.tilMelding())

            repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.FEILET), true).size shouldBeExactly 0
            val jobber = repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.OPPRETTET), true)

            jobber.size shouldBeExactly 2

            jobber[0].uuid shouldBe forespoerselMottattFail.kontekstId
            jobber[0].data.parseJson().toMap() shouldContainExactly forespoerselMottattFail.utloesendeMelding

            jobber[1].uuid shouldBe forespoerselBesvartFail.kontekstId
            jobber[1].data.parseJson().toMap() shouldContainExactly forespoerselBesvartFail.utloesendeMelding
        }

        test("setter jobb til STOPPET når maks antall forsøk er overskredet") {
            val omEttMinutt = LocalDateTime.now().plusMinutes(1)
            val feil = mockFail("bux life", EventName.INNTEKTSMELDING_JOURNALFOERT)

            repository.save(
                Bakgrunnsjobb(
                    feil.kontekstId,
                    FeilProsessor.JOB_TYPE,
                    forsoek = 4,
                    maksAntallForsoek = 3,
                    data = feil.utloesendeMelding.toJson().toString(),
                ),
            )

            rapid.sendJson(feil.tilMelding())

            val stoppede = repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, setOf(BakgrunnsjobbStatus.STOPPET), true)

            stoppede.size shouldBeExactly 1
        }

        context("ignorerer melding") {
            test("med event som ikke støttes") {
                val omEttMinutt = LocalDateTime.now().plusMinutes(1)
                val ikkeStoettetFail = mockFail("pux life", EventName.TILGANG_ORG_REQUESTED)

                rapid.sendJson(ikkeStoettetFail.tilMelding())

                val jobber = repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, BakgrunnsjobbStatus.entries.toSet(), true)

                jobber.size shouldBeExactly 0
            }

            test("uten event") {
                val omEttMinutt = LocalDateTime.now().plusMinutes(1)
                val stoettetFailUtenEvent =
                    mockFail("mux life", EventName.INNTEKTSMELDING_JOURNALFOERT).let {
                        it.copy(
                            utloesendeMelding =
                                it.utloesendeMelding
                                    .minus(Key.EVENT_NAME),
                        )
                    }

                rapid.sendJson(stoettetFailUtenEvent.tilMelding())

                val jobber = repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, BakgrunnsjobbStatus.entries.toSet(), true)

                jobber.size shouldBeExactly 0
            }

            test("med event som støttes, men med ugyldig feil") {
                val omEttMinutt = LocalDateTime.now().plusMinutes(1)
                val stoettetFail = mockFail("shux life", EventName.INNTEKTSMELDING_JOURNALFOERT)

                rapid.sendJson(
                    stoettetFail.tilMelding().plus(Key.FAIL to "ikke en fail".toJson()),
                )

                val jobber = repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, BakgrunnsjobbStatus.entries.toSet(), true)

                jobber.size shouldBeExactly 0
            }

            test("med event som støttes, men uten feil") {
                val omEttMinutt = LocalDateTime.now().plusMinutes(1)
                val stoettetFail = mockFail("delux life", EventName.INNTEKTSMELDING_JOURNALFOERT)

                rapid.sendJson(
                    stoettetFail.tilMelding().minus(Key.FAIL),
                )

                val jobber = repository.findByKjoeretidBeforeAndStatusIn(omEttMinutt, BakgrunnsjobbStatus.entries.toSet(), true)

                jobber.size shouldBeExactly 0
            }
        }
    })
