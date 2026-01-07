package no.nav.helsearbeidsgiver.inntektsmelding.db

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import no.nav.hag.simba.kontrakt.domene.inntektsmelding.LagretInntektsmelding
import no.nav.hag.simba.kontrakt.domene.inntektsmelding.test.mockEksternInntektsmelding
import no.nav.hag.simba.utils.db.exposed.test.FunSpecWithDb
import no.nav.hag.simba.utils.felles.test.mock.mockInntektsmeldingV1
import no.nav.hag.simba.utils.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.hag.simba.utils.felles.test.mock.randomDigitString
import no.nav.helsearbeidsgiver.inntektsmelding.db.tabell.InntektsmeldingEntitet
import no.nav.helsearbeidsgiver.utils.date.toOffsetDateTimeOslo
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.desember
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.juni
import no.nav.helsearbeidsgiver.utils.test.date.mai
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.date.september
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

private const val AVSENDER_NAVN = "Arve Avsender"

class TestRepo(
    private val db: Database,
) {
    fun hentRecordFraInntektsmelding(forespoerselId: UUID): ResultRow? =
        transaction(db) {
            InntektsmeldingEntitet
                .selectAll()
                .where {
                    InntektsmeldingEntitet.forespoerselId eq forespoerselId
                }.firstOrNull()
        }
}

class InntektsmeldingRepositoryTest :
    FunSpecWithDb(listOf(InntektsmeldingEntitet), { db ->

        val inntektsmeldingRepo = InntektsmeldingRepository(db)
        val testRepo = TestRepo(db)

        context(InntektsmeldingRepository::hentInntektsmelding.name) {
            test("henter skjema med inntektsmelding-ID") {
                val forespoerselId = UUID.randomUUID()
                val inntektsmeldingId = UUID.randomUUID()
                val mottatt = 20.september.atStartOfDay()

                val a = mockSkjemaInntektsmelding().copy(forespoerselId = forespoerselId)
                val b = mockEksternInntektsmelding().copy(tidspunkt = mottatt.plusHours(1))
                val c = mockSkjemaInntektsmelding().copy(forespoerselId = forespoerselId)
                val d = mockSkjemaInntektsmelding().copy(forespoerselId = forespoerselId)
                val e = mockEksternInntektsmelding().copy(tidspunkt = mottatt.plusHours(4))

                inntektsmeldingRepo.lagreInntektsmeldingSkjema(UUID.randomUUID(), a, AVSENDER_NAVN, mottatt)
                inntektsmeldingRepo.lagreEksternInntektsmelding(UUID.randomUUID(), forespoerselId, b)
                inntektsmeldingRepo.lagreInntektsmeldingSkjema(inntektsmeldingId, c, AVSENDER_NAVN, mottatt.plusHours(2))
                inntektsmeldingRepo.lagreInntektsmeldingSkjema(UUID.randomUUID(), d, AVSENDER_NAVN, mottatt.plusHours(3))
                inntektsmeldingRepo.lagreEksternInntektsmelding(UUID.randomUUID(), forespoerselId, e)

                val lagret = inntektsmeldingRepo.hentInntektsmelding(inntektsmeldingId)

                lagret shouldBe
                    LagretInntektsmelding.Skjema(
                        avsenderNavn = AVSENDER_NAVN,
                        skjema = c,
                        mottatt = mottatt.plusHours(2),
                    )
            }

            test("henter ekstern inntektsmelding med inntektsmelding-ID") {
                val forespoerselId = UUID.randomUUID()
                val inntektsmeldingId = UUID.randomUUID()
                val mottatt = 3.mai.atStartOfDay()

                val a = mockSkjemaInntektsmelding().copy(forespoerselId = forespoerselId)
                val b = mockEksternInntektsmelding().copy(tidspunkt = mottatt.plusHours(1))
                val c = mockEksternInntektsmelding().copy(tidspunkt = mottatt.plusHours(2))
                val d = mockSkjemaInntektsmelding().copy(forespoerselId = forespoerselId)
                val e = mockEksternInntektsmelding().copy(tidspunkt = mottatt.plusHours(4))

                inntektsmeldingRepo.lagreInntektsmeldingSkjema(UUID.randomUUID(), a, AVSENDER_NAVN, mottatt)
                inntektsmeldingRepo.lagreEksternInntektsmelding(UUID.randomUUID(), forespoerselId, b)
                inntektsmeldingRepo.lagreEksternInntektsmelding(inntektsmeldingId, forespoerselId, c)
                inntektsmeldingRepo.lagreInntektsmeldingSkjema(UUID.randomUUID(), d, AVSENDER_NAVN, mottatt.plusHours(3))
                inntektsmeldingRepo.lagreEksternInntektsmelding(UUID.randomUUID(), forespoerselId, e)

                val lagret = inntektsmeldingRepo.hentInntektsmelding(inntektsmeldingId)

                lagret shouldBe
                    LagretInntektsmelding.Ekstern(
                        ekstern = c,
                    )
            }

            test("tåler at det er ingenting å hente med inntektsmelding-ID") {
                inntektsmeldingRepo.lagreInntektsmeldingSkjema(UUID.randomUUID(), mockSkjemaInntektsmelding(), AVSENDER_NAVN, 16.juni.atStartOfDay())
                inntektsmeldingRepo.lagreEksternInntektsmelding(UUID.randomUUID(), UUID.randomUUID(), mockEksternInntektsmelding())

                val lagret = inntektsmeldingRepo.hentInntektsmelding(UUID.randomUUID())

                lagret.shouldBeNull()
            }
        }

        context(InntektsmeldingRepository::hentNyesteInntektsmelding.name) {

            test("henter nyeste") {
                val forespoerselId = UUID.randomUUID()
                val mottatt = 9.desember.atStartOfDay()
                val a = mockSkjemaInntektsmelding().copy(forespoerselId = forespoerselId)
                val b = mockEksternInntektsmelding().copy(tidspunkt = mottatt.plusHours(1))
                val c = mockSkjemaInntektsmelding().copy(forespoerselId = forespoerselId)

                inntektsmeldingRepo.lagreInntektsmeldingSkjema(UUID.randomUUID(), a, AVSENDER_NAVN, mottatt)
                inntektsmeldingRepo.lagreEksternInntektsmelding(UUID.randomUUID(), forespoerselId, b)
                inntektsmeldingRepo.lagreInntektsmeldingSkjema(UUID.randomUUID(), c, AVSENDER_NAVN, mottatt.plusHours(2))

                val lagret = inntektsmeldingRepo.hentNyesteInntektsmelding(forespoerselId)

                lagret shouldBe
                    LagretInntektsmelding.Skjema(
                        avsenderNavn = AVSENDER_NAVN,
                        skjema = c,
                        mottatt = mottatt.plusHours(2),
                    )
            }

            test("henter skjema (uten inntektsmelding)") {
                val skjema = mockSkjemaInntektsmelding()
                val mottatt = 9.desember.atStartOfDay()

                inntektsmeldingRepo.lagreInntektsmeldingSkjema(UUID.randomUUID(), skjema, null, mottatt)

                val lagret = inntektsmeldingRepo.hentNyesteInntektsmelding(skjema.forespoerselId)

                lagret shouldBe
                    LagretInntektsmelding.Skjema(
                        avsenderNavn = null,
                        skjema = skjema,
                        mottatt = mottatt,
                    )
            }

            test("henter skjema (med inntektsmelding)") {
                val skjema = mockSkjemaInntektsmelding()
                val mottatt = 9.desember.atStartOfDay()
                // Bruk inntektsmelding som er ulikt skjema for å sjekke at hentet skjema ikke stammer fra inntektsmelding
                val inntektsmelding = mockInntektsmeldingV1().copy(inntekt = null, mottatt = mottatt.toOffsetDateTimeOslo())

                inntektsmeldingRepo.lagreInntektsmeldingSkjema(inntektsmelding.id, skjema, AVSENDER_NAVN, mottatt)
                inntektsmeldingRepo.oppdaterMedInntektsmelding(inntektsmelding)

                val lagret = inntektsmeldingRepo.hentNyesteInntektsmelding(skjema.forespoerselId)

                lagret shouldBe
                    LagretInntektsmelding.Skjema(
                        avsenderNavn = inntektsmelding.avsender.navn,
                        skjema = skjema,
                        mottatt = mottatt,
                    )
            }

            test("henter ekstern inntektsmelding") {
                val forespoerselId = UUID.randomUUID()
                val eksternInntektsmelding = mockEksternInntektsmelding()

                inntektsmeldingRepo.lagreEksternInntektsmelding(UUID.randomUUID(), forespoerselId, eksternInntektsmelding)

                val lagret = inntektsmeldingRepo.hentNyesteInntektsmelding(forespoerselId)

                lagret shouldBe LagretInntektsmelding.Ekstern(eksternInntektsmelding)
            }

            test("tåler at det er ingenting å hente") {
                inntektsmeldingRepo.lagreInntektsmeldingSkjema(UUID.randomUUID(), mockSkjemaInntektsmelding(), AVSENDER_NAVN, 9.desember.atStartOfDay())

                val lagret = inntektsmeldingRepo.hentNyesteInntektsmelding(UUID.randomUUID())

                lagret.shouldBeNull()
            }
        }

        context(InntektsmeldingRepository::hentNyesteInntektsmeldingSkjema.name) {
            test("henter nyeste inntektsmeldingsskjema") {
                transaction {
                    InntektsmeldingEntitet.selectAll().toList()
                }.shouldBeEmpty()

                val forespoerselId = UUID.randomUUID()
                val skjema1 = mockSkjemaInntektsmelding().copy(forespoerselId = forespoerselId)
                val skjema2 = mockSkjemaInntektsmelding().copy(forespoerselId = forespoerselId, refusjon = null)
                val mottatt = 17.april.atStartOfDay()

                inntektsmeldingRepo.lagreInntektsmeldingSkjema(UUID.randomUUID(), skjema1, AVSENDER_NAVN, mottatt)
                inntektsmeldingRepo.hentNyesteInntektsmeldingSkjema(forespoerselId) shouldBe skjema1

                inntektsmeldingRepo.lagreInntektsmeldingSkjema(UUID.randomUUID(), skjema2, AVSENDER_NAVN, mottatt.plusHours(3))
                inntektsmeldingRepo.hentNyesteInntektsmeldingSkjema(forespoerselId) shouldBe skjema2

                inntektsmeldingRepo.hentNyesteInntektsmeldingSkjema(UUID.randomUUID()) shouldBe null
            }
        }

        context(InntektsmeldingRepository::hentNyesteInntektsmeldingId.name) {
            test("henter nyeste inntektsmelding-ID") {
                transaction {
                    InntektsmeldingEntitet.selectAll().toList()
                }.shouldBeEmpty()

                val skjema = mockSkjemaInntektsmelding()
                val mottatt = 27.mars.atStartOfDay()
                val inntektsmeldingId1 = UUID.randomUUID()
                val inntektsmeldingId2 = UUID.randomUUID()

                inntektsmeldingRepo.lagreInntektsmeldingSkjema(inntektsmeldingId1, skjema, AVSENDER_NAVN, mottatt)
                inntektsmeldingRepo.hentNyesteInntektsmeldingId(skjema.forespoerselId) shouldBe inntektsmeldingId1

                inntektsmeldingRepo.lagreInntektsmeldingSkjema(inntektsmeldingId2, skjema, AVSENDER_NAVN, mottatt.plusHours(3))
                inntektsmeldingRepo.hentNyesteInntektsmeldingId(skjema.forespoerselId) shouldBe inntektsmeldingId2

                inntektsmeldingRepo.hentNyesteInntektsmeldingId(UUID.randomUUID()) shouldBe null
            }
        }

        context(InntektsmeldingRepository::lagreInntektsmeldingSkjema.name) {
            test("lagrer inntektsmeldingsskjema") {
                transaction {
                    InntektsmeldingEntitet.selectAll().toList()
                }.shouldBeEmpty()

                val inntektsmeldingId = UUID.randomUUID()
                val skjema = mockSkjemaInntektsmelding()
                val mottatt = 11.januar.atStartOfDay()

                inntektsmeldingRepo.lagreInntektsmeldingSkjema(inntektsmeldingId, skjema, AVSENDER_NAVN, mottatt)

                val record = testRepo.hentRecordFraInntektsmelding(skjema.forespoerselId).shouldNotBeNull()

                record.getOrNull(InntektsmeldingEntitet.inntektsmeldingId) shouldBe inntektsmeldingId
                record.getOrNull(InntektsmeldingEntitet.forespoerselId) shouldBe skjema.forespoerselId
                record.getOrNull(InntektsmeldingEntitet.skjema) shouldBe skjema
                record.getOrNull(InntektsmeldingEntitet.inntektsmelding) shouldBe null
                record.getOrNull(InntektsmeldingEntitet.eksternInntektsmelding) shouldBe null
                record.getOrNull(InntektsmeldingEntitet.avsenderNavn) shouldBe AVSENDER_NAVN
                record.getOrNull(InntektsmeldingEntitet.journalpostId) shouldBe null
                record.getOrNull(InntektsmeldingEntitet.innsendt) shouldBe mottatt
                record.getOrNull(InntektsmeldingEntitet.prosessert) shouldBe null
            }

            test("lagrer inntektsmeldingsskjema uten avsendernavn") {
                transaction {
                    InntektsmeldingEntitet.selectAll().toList()
                }.shouldBeEmpty()

                val inntektsmeldingId = UUID.randomUUID()
                val skjema = mockSkjemaInntektsmelding()
                val mottatt = 11.januar.atStartOfDay()

                inntektsmeldingRepo.lagreInntektsmeldingSkjema(inntektsmeldingId, skjema, null, mottatt)

                val record = testRepo.hentRecordFraInntektsmelding(skjema.forespoerselId).shouldNotBeNull()

                record.getOrNull(InntektsmeldingEntitet.inntektsmeldingId) shouldBe inntektsmeldingId
                record.getOrNull(InntektsmeldingEntitet.forespoerselId) shouldBe skjema.forespoerselId
                record.getOrNull(InntektsmeldingEntitet.skjema) shouldBe skjema
                record.getOrNull(InntektsmeldingEntitet.inntektsmelding) shouldBe null
                record.getOrNull(InntektsmeldingEntitet.eksternInntektsmelding) shouldBe null
                record.getOrNull(InntektsmeldingEntitet.avsenderNavn) shouldBe null
                record.getOrNull(InntektsmeldingEntitet.journalpostId) shouldBe null
                record.getOrNull(InntektsmeldingEntitet.innsendt) shouldBe mottatt
                record.getOrNull(InntektsmeldingEntitet.prosessert) shouldBe null
            }

            test("klarer ikke lagre flere skjema på samme inntektsmelding-ID") {
                val inntektsmeldingId = UUID.randomUUID()
                val mottatt = 11.januar.atStartOfDay()

                shouldNotThrowAny {
                    inntektsmeldingRepo.lagreInntektsmeldingSkjema(inntektsmeldingId, mockSkjemaInntektsmelding(), AVSENDER_NAVN, mottatt)
                }
                shouldThrowExactly<ExposedSQLException> {
                    inntektsmeldingRepo.lagreInntektsmeldingSkjema(inntektsmeldingId, mockSkjemaInntektsmelding(), AVSENDER_NAVN, mottatt)
                }
                shouldThrowExactly<ExposedSQLException> {
                    inntektsmeldingRepo.lagreEksternInntektsmelding(inntektsmeldingId, UUID.randomUUID(), mockEksternInntektsmelding())
                }
            }

            test("lagrer flere skjema på samme forespørsel-ID") {
                val skjema = mockSkjemaInntektsmelding()
                val mottatt = 11.januar.atStartOfDay()

                shouldNotThrowAny {
                    inntektsmeldingRepo.lagreInntektsmeldingSkjema(UUID.randomUUID(), skjema, AVSENDER_NAVN, mottatt)
                    inntektsmeldingRepo.lagreInntektsmeldingSkjema(UUID.randomUUID(), skjema, AVSENDER_NAVN, mottatt)
                }
            }
        }

        context(InntektsmeldingRepository::lagreEksternInntektsmelding.name) {
            test("lagrer ekstern inntektsmelding") {
                transaction {
                    InntektsmeldingEntitet.selectAll().toList()
                }.shouldBeEmpty()

                val inntektsmeldingId = UUID.randomUUID()
                val forespoerselId = UUID.randomUUID()
                val eksternIm = mockEksternInntektsmelding()

                inntektsmeldingRepo.lagreEksternInntektsmelding(inntektsmeldingId, forespoerselId, eksternIm)

                val record = testRepo.hentRecordFraInntektsmelding(forespoerselId).shouldNotBeNull()

                record.getOrNull(InntektsmeldingEntitet.inntektsmeldingId) shouldBe inntektsmeldingId
                record.getOrNull(InntektsmeldingEntitet.forespoerselId) shouldBe forespoerselId
                record.getOrNull(InntektsmeldingEntitet.skjema) shouldBe null
                record.getOrNull(InntektsmeldingEntitet.inntektsmelding) shouldBe null
                record.getOrNull(InntektsmeldingEntitet.eksternInntektsmelding) shouldBe eksternIm
                record.getOrNull(InntektsmeldingEntitet.avsenderNavn) shouldBe null
                record.getOrNull(InntektsmeldingEntitet.journalpostId) shouldBe null
                record.getOrNull(InntektsmeldingEntitet.innsendt) shouldBe eksternIm.tidspunkt
                record.getOrNull(InntektsmeldingEntitet.prosessert) shouldBe null
            }

            test("klarer ikke lagre flere inntektsmeldinger på samme inntektsmelding-ID") {
                val inntektsmeldingId = UUID.randomUUID()

                shouldNotThrowAny {
                    inntektsmeldingRepo.lagreEksternInntektsmelding(inntektsmeldingId, UUID.randomUUID(), mockEksternInntektsmelding())
                }
                shouldThrowExactly<ExposedSQLException> {
                    inntektsmeldingRepo.lagreEksternInntektsmelding(inntektsmeldingId, UUID.randomUUID(), mockEksternInntektsmelding())
                }
                shouldThrowExactly<ExposedSQLException> {
                    inntektsmeldingRepo.lagreInntektsmeldingSkjema(inntektsmeldingId, mockSkjemaInntektsmelding(), AVSENDER_NAVN, 4.april.atStartOfDay())
                }
            }

            test("lagrer flere eksterne innteksmeldinger på samme forespørsel-ID") {
                val forespoerselId = UUID.randomUUID()

                shouldNotThrowAny {
                    inntektsmeldingRepo.lagreEksternInntektsmelding(UUID.randomUUID(), forespoerselId, mockEksternInntektsmelding())
                    inntektsmeldingRepo.lagreEksternInntektsmelding(UUID.randomUUID(), forespoerselId, mockEksternInntektsmelding())
                }
            }
        }

        context(InntektsmeldingRepository::oppdaterMedInntektsmelding.name) {
            test("lagrer inntektsmelding") {
                transaction {
                    InntektsmeldingEntitet.selectAll().toList()
                }.shouldBeEmpty()

                val skjema = mockSkjemaInntektsmelding()
                val mottatt = 9.desember.atStartOfDay()
                val inntektsmelding = mockInntektsmeldingV1().copy(mottatt = mottatt.toOffsetDateTimeOslo())

                inntektsmeldingRepo.lagreInntektsmeldingSkjema(inntektsmelding.id, skjema, AVSENDER_NAVN, mottatt)
                inntektsmeldingRepo.oppdaterMedInntektsmelding(inntektsmelding)

                val record = testRepo.hentRecordFraInntektsmelding(skjema.forespoerselId).shouldNotBeNull()

                record.getOrNull(InntektsmeldingEntitet.inntektsmeldingId) shouldBe inntektsmelding.id
                record.getOrNull(InntektsmeldingEntitet.forespoerselId) shouldBe skjema.forespoerselId
                record.getOrNull(InntektsmeldingEntitet.skjema) shouldBe skjema
                record.getOrNull(InntektsmeldingEntitet.inntektsmelding) shouldBe inntektsmelding
                record.getOrNull(InntektsmeldingEntitet.eksternInntektsmelding) shouldBe null
                record.getOrNull(InntektsmeldingEntitet.avsenderNavn) shouldBe inntektsmelding.avsender.navn
                record.getOrNull(InntektsmeldingEntitet.journalpostId) shouldBe null
                record.getOrNull(InntektsmeldingEntitet.innsendt) shouldBe mottatt
                record.getOrNull(InntektsmeldingEntitet.prosessert) shouldBe null
            }
        }

        context(InntektsmeldingRepository::oppdaterMedJournalpostId.name) {
            test("skal oppdatere inntektsmelding med journalpostId") {
                val skjema = mockSkjemaInntektsmelding()
                val mottatt = 16.mars.atStartOfDay()
                val inntektsmelding = mockInntektsmeldingV1().copy(mottatt = mottatt.toOffsetDateTimeOslo())
                val inntektsmeldingId1 = UUID.randomUUID()
                val inntektsmeldingId2 = UUID.randomUUID()
                val journalpostId = randomDigitString(9)

                inntektsmeldingRepo.lagreInntektsmeldingSkjema(inntektsmeldingId1, skjema, AVSENDER_NAVN, mottatt)
                inntektsmeldingRepo.lagreInntektsmeldingSkjema(inntektsmeldingId2, skjema, AVSENDER_NAVN, mottatt.plusHours(3))

                inntektsmeldingRepo.oppdaterMedInntektsmelding(inntektsmelding.copy(id = inntektsmeldingId1))
                inntektsmeldingRepo.oppdaterMedInntektsmelding(inntektsmelding.copy(id = inntektsmeldingId2))

                // Skal kun oppdatere den andre av de to inntektsmeldingene
                inntektsmeldingRepo.oppdaterMedJournalpostId(inntektsmeldingId2, journalpostId)

                val resultat =
                    transaction(db) {
                        InntektsmeldingEntitet
                            .selectAll()
                            .orderBy(InntektsmeldingEntitet.innsendt)
                            .toList()
                    }

                resultat shouldHaveSize 2

                InntektsmeldingEntitet.apply {
                    resultat[0][innsendt] shouldBeLessThan resultat[1][innsendt]

                    resultat[0][this.inntektsmelding].shouldNotBeNull()
                    resultat[0][this.journalpostId].shouldBeNull()

                    resultat[1][this.inntektsmelding].shouldNotBeNull()
                    resultat[1][this.journalpostId] shouldBe journalpostId
                }
            }

            test("skal oppdatere inntektsmelding med journalpostId selv om den allerede har dette") {
                val skjema = mockSkjemaInntektsmelding()
                val mottatt = 5.mars.atStartOfDay()
                val inntektsmelding = mockInntektsmeldingV1().copy(mottatt = mottatt.toOffsetDateTimeOslo())
                val inntektsmeldingId1 = UUID.randomUUID()
                val inntektsmeldingId2 = UUID.randomUUID()
                val gammelJournalpostId = "jp-traust-gevir"
                val nyJournalpostId = "jp-gallant-badehette"

                inntektsmeldingRepo.lagreInntektsmeldingSkjema(inntektsmeldingId1, skjema, AVSENDER_NAVN, mottatt)
                inntektsmeldingRepo.lagreInntektsmeldingSkjema(inntektsmeldingId2, skjema, AVSENDER_NAVN, mottatt.plusHours(3))

                inntektsmeldingRepo.oppdaterMedInntektsmelding(inntektsmelding.copy(id = inntektsmeldingId1))
                inntektsmeldingRepo.oppdaterMedInntektsmelding(inntektsmelding.copy(id = inntektsmeldingId2))

                inntektsmeldingRepo.oppdaterMedJournalpostId(inntektsmeldingId2, gammelJournalpostId)

                val resultatFoerNyJournalpostId =
                    transaction(db) {
                        InntektsmeldingEntitet
                            .selectAll()
                            .orderBy(InntektsmeldingEntitet.innsendt)
                            .toList()
                    }

                resultatFoerNyJournalpostId shouldHaveSize 2

                InntektsmeldingEntitet.apply {
                    resultatFoerNyJournalpostId[0][innsendt] shouldBeLessThan resultatFoerNyJournalpostId[1][innsendt]

                    resultatFoerNyJournalpostId[0][this.inntektsmelding].shouldNotBeNull()
                    resultatFoerNyJournalpostId[0][journalpostId].shouldBeNull()

                    resultatFoerNyJournalpostId[1][this.inntektsmelding].shouldNotBeNull()
                    resultatFoerNyJournalpostId[1][journalpostId] shouldBe gammelJournalpostId
                }

                // Oppdater journalpostId
                inntektsmeldingRepo.oppdaterMedJournalpostId(inntektsmeldingId2, nyJournalpostId)

                val resultsEtterNyJournalpostId =
                    transaction(db) {
                        InntektsmeldingEntitet
                            .selectAll()
                            .orderBy(InntektsmeldingEntitet.innsendt)
                            .toList()
                    }

                resultsEtterNyJournalpostId shouldHaveSize 2

                InntektsmeldingEntitet.apply {
                    resultsEtterNyJournalpostId[0][innsendt] shouldBeLessThan resultsEtterNyJournalpostId[1][innsendt]

                    resultsEtterNyJournalpostId[0][this.inntektsmelding].shouldNotBeNull()
                    resultsEtterNyJournalpostId[0][journalpostId].shouldBeNull()

                    resultsEtterNyJournalpostId[1][this.inntektsmelding].shouldNotBeNull()
                    resultsEtterNyJournalpostId[1][journalpostId] shouldBe nyJournalpostId
                }
            }

            test("skal _ikke_ oppdatere journalpostId for ekstern inntektsmelding") {
                val skjema = mockSkjemaInntektsmelding()
                val mottatt = 9.desember.atStartOfDay()
                val inntektsmelding = mockInntektsmeldingV1().copy(mottatt = mottatt.toOffsetDateTimeOslo())
                val journalpostId = "jp-slem-fryser"

                inntektsmeldingRepo.lagreInntektsmeldingSkjema(inntektsmelding.id, skjema, AVSENDER_NAVN, mottatt)
                inntektsmeldingRepo.oppdaterMedInntektsmelding(inntektsmelding)

                inntektsmeldingRepo.lagreEksternInntektsmelding(
                    UUID.randomUUID(),
                    skjema.forespoerselId,
                    mockEksternInntektsmelding().copy(tidspunkt = mottatt.plusHours(1)),
                )

                inntektsmeldingRepo.oppdaterMedJournalpostId(inntektsmelding.id, journalpostId)

                val resultat =
                    transaction(db) {
                        InntektsmeldingEntitet
                            .selectAll()
                            .orderBy(InntektsmeldingEntitet.innsendt)
                            .toList()
                    }

                resultat shouldHaveSize 2

                InntektsmeldingEntitet.apply {
                    resultat[0][innsendt] shouldBeLessThan resultat[1][innsendt]

                    resultat[0][this.inntektsmelding].shouldNotBeNull()
                    resultat[0][this.journalpostId] shouldBe journalpostId

                    resultat[1][eksternInntektsmelding].shouldNotBeNull()
                    resultat[1][this.journalpostId].shouldBeNull()
                }
            }
        }

        context(InntektsmeldingRepository::oppdaterSomProsessert.name) {
            test("skal oppdatere im som prosessert") {
                val skjema = mockSkjemaInntektsmelding()
                val mottatt = 26.april.atStartOfDay()
                val inntektsmeldingId1 = UUID.randomUUID()
                val inntektsmeldingId2 = UUID.randomUUID()

                inntektsmeldingRepo.lagreInntektsmeldingSkjema(inntektsmeldingId1, skjema, AVSENDER_NAVN, mottatt)
                inntektsmeldingRepo.lagreInntektsmeldingSkjema(inntektsmeldingId2, mockSkjemaInntektsmelding(), AVSENDER_NAVN, mottatt.plusHours(4))

                inntektsmeldingRepo.oppdaterSomProsessert(inntektsmeldingId1)

                val resultat =
                    transaction(db) {
                        InntektsmeldingEntitet
                            .selectAll()
                            .orderBy(InntektsmeldingEntitet.innsendt)
                            .toList()
                    }

                resultat shouldHaveSize 2

                val prosessertVindu =
                    LocalDateTime.now().let {
                        it.minusMinutes(1)..it.plusMinutes(1)
                    }

                InntektsmeldingEntitet.apply {
                    resultat[0][innsendt] shouldBeLessThan resultat[1][innsendt]

                    resultat[0][this.skjema] shouldBe skjema
                    resultat[0][prosessert].shouldNotBeNull().shouldBeIn(prosessertVindu)

                    resultat[1][this.skjema].shouldNotBeNull()
                    resultat[1][prosessert].shouldBeNull()
                }
            }
        }
    })
