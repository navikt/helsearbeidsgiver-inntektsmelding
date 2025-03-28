package no.nav.helsearbeidsgiver.inntektsmelding.db

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.db.exposed.test.FunSpecWithDb
import no.nav.helsearbeidsgiver.felles.domene.LagretInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.mock.mockArbeidsgiverperiode
import no.nav.helsearbeidsgiver.felles.test.mock.mockEksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntekt
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.felles.test.mock.mockRefusjon
import no.nav.helsearbeidsgiver.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.mock.randomDigitString
import no.nav.helsearbeidsgiver.felles.utils.toOffsetDateTimeOslo
import no.nav.helsearbeidsgiver.inntektsmelding.db.domene.convert
import no.nav.helsearbeidsgiver.inntektsmelding.db.tabell.InntektsmeldingEntitet
import no.nav.helsearbeidsgiver.utils.test.date.desember
import no.nav.helsearbeidsgiver.utils.test.date.mars
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

class TestRepo(
    private val db: Database,
) {
    fun hentRecordFraInntektsmelding(forespoerselId: UUID): ResultRow? =
        transaction(db) {
            InntektsmeldingEntitet
                .selectAll()
                .where {
                    InntektsmeldingEntitet.forespoerselId eq forespoerselId.toString()
                }.firstOrNull()
        }
}

class InntektsmeldingRepositoryTest :
    FunSpecWithDb(listOf(InntektsmeldingEntitet), { db ->

        val inntektsmeldingRepo = InntektsmeldingRepository(db)
        val testRepo = TestRepo(db)

        test("skal lagre inntektsmeldingskjema og dokument") {
            transaction {
                InntektsmeldingEntitet.selectAll().toList()
            }.shouldBeEmpty()

            val skjema = mockSkjemaInntektsmelding()

            val innsendingId = inntektsmeldingRepo.lagreInntektsmeldingSkjema(skjema, 9.desember.atStartOfDay())

            val beriketDokument = mockInntektsmeldingV1().copy(mottatt = OffsetDateTime.now())
            inntektsmeldingRepo.oppdaterMedBeriketDokument(innsendingId, beriketDokument)

            val record = testRepo.hentRecordFraInntektsmelding(skjema.forespoerselId).shouldNotBeNull()

            record.getOrNull(InntektsmeldingEntitet.journalpostId) shouldBe null
            record.getOrNull(InntektsmeldingEntitet.eksternInntektsmelding) shouldBe null
            record.getOrNull(InntektsmeldingEntitet.skjema) shouldBe skjema
            record.getOrNull(InntektsmeldingEntitet.dokument) shouldBe beriketDokument.convert()

            inntektsmeldingRepo.hentNyesteBerikedeInnsendingId(skjema.forespoerselId) shouldBe innsendingId
        }

        test("skal lagre hvert innsendte skjema med ny innsendingId, men hente nyeste berikede inntektsmelding") {
            transaction {
                InntektsmeldingEntitet.selectAll().toList()
            }.shouldBeEmpty()

            val skjema = mockSkjemaInntektsmelding()
            val mottatt = 27.mars.atStartOfDay()

            val innsendingId1 = inntektsmeldingRepo.lagreInntektsmeldingSkjema(skjema, mottatt)
            val innsendingId2 = inntektsmeldingRepo.lagreInntektsmeldingSkjema(skjema, mottatt.plusHours(3))
            inntektsmeldingRepo.lagreInntektsmeldingSkjema(skjema, mottatt.plusHours(6))

            innsendingId1 shouldNotBeEqual innsendingId2

            val beriketDokument = mockInntektsmeldingV1().copy(mottatt = OffsetDateTime.now())

            inntektsmeldingRepo.oppdaterMedBeriketDokument(innsendingId2, beriketDokument.copy(id = UUID.randomUUID()))
            inntektsmeldingRepo.hentNyesteBerikedeInnsendingId(skjema.forespoerselId) shouldBe innsendingId2

            inntektsmeldingRepo.oppdaterMedBeriketDokument(innsendingId1, beriketDokument.copy(id = UUID.randomUUID()))
            inntektsmeldingRepo.hentNyesteBerikedeInnsendingId(skjema.forespoerselId) shouldBe innsendingId2
        }

        test("skal returnere im med gammelt inntekt-format ok") {
            transaction {
                InntektsmeldingEntitet.selectAll().toList()
            }.shouldBeEmpty()

            val skjema = mockSkjemaInntektsmelding()
            val inntektsmelding = mockInntektsmeldingV1()

            val innsendingId = inntektsmeldingRepo.lagreInntektsmeldingSkjema(skjema, 9.desember.atStartOfDay())

            inntektsmeldingRepo.oppdaterMedBeriketDokument(innsendingId, inntektsmelding)

            transaction {
                InntektsmeldingEntitet
                    .selectAll()
                    .where {
                        (InntektsmeldingEntitet.forespoerselId eq skjema.forespoerselId.toString()) and
                            (InntektsmeldingEntitet.dokument eq inntektsmelding.convert())
                    }.single()
            }
        }

        test("skal oppdatere journalpostId") {
            transaction {
                InntektsmeldingEntitet.selectAll().toList()
            }.shouldBeEmpty()

            val skjema = mockSkjemaInntektsmelding()
            val journalpost1 = randomDigitString(7)

            val innsendingId = inntektsmeldingRepo.lagreInntektsmeldingSkjema(skjema, 9.desember.atStartOfDay())
            val beriketDokument = mockInntektsmeldingV1().copy(mottatt = OffsetDateTime.now())
            inntektsmeldingRepo.oppdaterMedBeriketDokument(innsendingId, beriketDokument)

            inntektsmeldingRepo.oppdaterJournalpostId(innsendingId, journalpost1)

            val record = testRepo.hentRecordFraInntektsmelding(skjema.forespoerselId)

            record.shouldNotBeNull()

            val journalPostId = record.getOrNull(InntektsmeldingEntitet.journalpostId)
            journalPostId.shouldNotBeNull()
        }

        test("skal oppdatere im med journalpostId") {
            val skjema = mockSkjemaInntektsmelding()
            val journalpostId = "jp-mollefonken-kjele"
            val mottatt = 16.mars.atStartOfDay()

            val innsendingId1 = inntektsmeldingRepo.lagreInntektsmeldingSkjema(skjema, mottatt)
            val innsendingId2 = inntektsmeldingRepo.lagreInntektsmeldingSkjema(skjema, mottatt.plusHours(3))

            val beriketDokument = mockInntektsmeldingV1().copy(mottatt = OffsetDateTime.now())
            inntektsmeldingRepo.oppdaterMedBeriketDokument(innsendingId1, beriketDokument.copy(id = UUID.randomUUID()))
            inntektsmeldingRepo.oppdaterMedBeriketDokument(innsendingId2, beriketDokument.copy(id = UUID.randomUUID()))

            // Skal kun oppdatere den andre av de to inntektsmeldingene
            inntektsmeldingRepo.oppdaterJournalpostId(innsendingId2, journalpostId)

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

                resultat[0][dokument].shouldNotBeNull()
                resultat[0][this.journalpostId].shouldBeNull()

                resultat[1][dokument].shouldNotBeNull()
                resultat[1][this.journalpostId] shouldBe journalpostId
            }
        }

        test("skal oppdatere inntektsmelding med journalpostId selv om den allerede har dette") {
            val skjema = mockSkjemaInntektsmelding()
            val gammelJournalpostId = "jp-traust-gevir"
            val nyJournalpostId = "jp-gallant-badehette"
            val mottatt = 5.mars.atStartOfDay()

            val innsendingId1 = inntektsmeldingRepo.lagreInntektsmeldingSkjema(skjema, mottatt)
            val innsendingId2 = inntektsmeldingRepo.lagreInntektsmeldingSkjema(skjema, mottatt.plusHours(3))

            val beriketDokument = mockInntektsmeldingV1().copy(mottatt = OffsetDateTime.now())
            inntektsmeldingRepo.oppdaterMedBeriketDokument(innsendingId1, beriketDokument.copy(id = UUID.randomUUID()))
            inntektsmeldingRepo.oppdaterMedBeriketDokument(innsendingId2, beriketDokument.copy(id = UUID.randomUUID()))

            inntektsmeldingRepo.oppdaterJournalpostId(innsendingId2, gammelJournalpostId)

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

                resultatFoerNyJournalpostId[0][dokument].shouldNotBeNull()
                resultatFoerNyJournalpostId[0][journalpostId].shouldBeNull()

                resultatFoerNyJournalpostId[1][dokument].shouldNotBeNull()
                resultatFoerNyJournalpostId[1][journalpostId] shouldBe gammelJournalpostId
            }

            // Oppdater journalpostId
            inntektsmeldingRepo.oppdaterJournalpostId(innsendingId2, nyJournalpostId)

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

                resultsEtterNyJournalpostId[0][dokument].shouldNotBeNull()
                resultsEtterNyJournalpostId[0][journalpostId].shouldBeNull()

                resultsEtterNyJournalpostId[1][dokument].shouldNotBeNull()
                resultsEtterNyJournalpostId[1][journalpostId] shouldBe nyJournalpostId
            }
        }

        test("skal _ikke_ oppdatere journalpostId for ekstern inntektsmelding") {
            val skjema = mockSkjemaInntektsmelding()
            val mottatt = 9.desember.atStartOfDay()
            val journalpostId = "jp-slem-fryser"

            val innsendingId1 = inntektsmeldingRepo.lagreInntektsmeldingSkjema(skjema, mottatt)

            val beriketDokument = mockInntektsmeldingV1().copy(mottatt = mottatt.toOffsetDateTimeOslo())
            inntektsmeldingRepo.oppdaterMedBeriketDokument(innsendingId1, beriketDokument)
            inntektsmeldingRepo.lagreEksternInntektsmelding(skjema.forespoerselId, mockEksternInntektsmelding().copy(tidspunkt = mottatt.plusHours(1)))

            inntektsmeldingRepo.oppdaterJournalpostId(innsendingId1, journalpostId)

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

                resultat[0][dokument].shouldNotBeNull()
                resultat[0][this.journalpostId] shouldBe journalpostId

                resultat[1][eksternInntektsmelding].shouldNotBeNull()
                resultat[1][this.journalpostId].shouldBeNull()
            }
        }

        context(InntektsmeldingRepository::hentNyesteInntektsmelding.name) {

            test("henter nyeste") {
                val forespoerselId = UUID.randomUUID()
                val mottatt = 9.desember.atStartOfDay()
                val a = mockSkjemaInntektsmelding().copy(forespoerselId = forespoerselId)
                val b = mockEksternInntektsmelding().copy(tidspunkt = mottatt.plusHours(1))
                val c = mockSkjemaInntektsmelding().copy(forespoerselId = forespoerselId)

                inntektsmeldingRepo.lagreInntektsmeldingSkjema(a, mottatt)
                inntektsmeldingRepo.lagreEksternInntektsmelding(forespoerselId, b)
                inntektsmeldingRepo.lagreInntektsmeldingSkjema(c, mottatt.plusHours(2))

                val lagret = inntektsmeldingRepo.hentNyesteInntektsmelding(forespoerselId)

                lagret shouldBe
                    LagretInntektsmelding.Skjema(
                        avsenderNavn = null,
                        skjema = c,
                        mottatt = mottatt.plusHours(2),
                    )
            }

            test("henter skjema (uten inntektsmelding)") {
                val skjema = mockSkjemaInntektsmelding()
                val mottatt = 9.desember.atStartOfDay()

                val skjemaUtenEndringsAarsaker = skjema.copy(inntekt = skjema.inntekt?.copy(endringAarsaker = emptyList()))

                inntektsmeldingRepo.lagreInntektsmeldingSkjema(skjemaUtenEndringsAarsaker, mottatt)

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
                // Bruk inntektsmelding som er ulikt skjema for å sjekke at hentet skjema ikke stammer fra inntektsmelding
                val inntektsmelding = mockInntektsmeldingV1().copy(inntekt = null)
                val mottatt = 9.desember.atStartOfDay()

                val innsendingId = inntektsmeldingRepo.lagreInntektsmeldingSkjema(skjema, mottatt)
                inntektsmeldingRepo.oppdaterMedBeriketDokument(innsendingId, inntektsmelding)

                val lagret = inntektsmeldingRepo.hentNyesteInntektsmelding(skjema.forespoerselId)

                lagret shouldBe
                    LagretInntektsmelding.Skjema(
                        avsenderNavn = inntektsmelding.avsender.navn,
                        skjema = skjema,
                        mottatt = mottatt,
                    )
            }

            test("henter inntektsmelding som skjema") {
                val forespoerselId = UUID.randomUUID()
                val inntektsmelding = mockInntektsmeldingV1()
                val mottatt = 9.desember.atStartOfDay()

                transaction(db) {
                    InntektsmeldingEntitet.insert {
                        it[this.forespoerselId] = forespoerselId.toString()
                        it[dokument] = inntektsmelding.convert()
                        it[innsendt] = mottatt
                    }
                }

                val lagret = inntektsmeldingRepo.hentNyesteInntektsmelding(forespoerselId)

                val forventetSkjema =
                    SkjemaInntektsmelding(
                        forespoerselId = forespoerselId,
                        avsenderTlf = inntektsmelding.avsender.tlf.orEmpty(),
                        agp = mockArbeidsgiverperiode(),
                        inntekt = mockInntekt(),
                        refusjon = mockRefusjon(),
                    )

                lagret shouldBe
                    LagretInntektsmelding.Skjema(
                        avsenderNavn = "Nifs Krumkake",
                        skjema = forventetSkjema,
                        mottatt = mottatt,
                    )
            }

            test("henter ekstern inntektsmelding") {
                val forespoerselId = UUID.randomUUID()
                val eksternInntektsmelding = mockEksternInntektsmelding()

                inntektsmeldingRepo.lagreEksternInntektsmelding(forespoerselId, eksternInntektsmelding)

                val lagret = inntektsmeldingRepo.hentNyesteInntektsmelding(forespoerselId)

                lagret shouldBe LagretInntektsmelding.Ekstern(eksternInntektsmelding)
            }

            test("tåler at det er ingenting å hente") {
                inntektsmeldingRepo.lagreInntektsmeldingSkjema(mockSkjemaInntektsmelding(), 9.desember.atStartOfDay())

                val lagret = inntektsmeldingRepo.hentNyesteInntektsmelding(UUID.randomUUID())

                lagret.shouldBeNull()
            }
        }
    })
