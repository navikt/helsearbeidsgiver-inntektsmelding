package no.nav.helsearbeidsgiver.inntektsmelding.db

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.delay
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Naturalytelse
import no.nav.helsearbeidsgiver.felles.db.exposed.test.FunSpecWithDb
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.felles.test.mock.randomDigitString
import no.nav.helsearbeidsgiver.inntektsmelding.db.tabell.SelvbestemtInntektsmeldingEntitet
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import no.nav.helsearbeidsgiver.utils.test.date.september
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class SelvbestemtImRepoTest :
    FunSpecWithDb(listOf(SelvbestemtInntektsmeldingEntitet), { db ->

        val selvbestemtImRepo = SelvbestemtImRepo(db)

        context(SelvbestemtImRepo::hentNyesteIm.name) {

            test("henter nyeste") {
                val selvbestemtId = UUID.randomUUID()
                val originalInntektsmelding =
                    mockInntektsmeldingV1().copy(
                        type =
                            Inntektsmelding.Type.Selvbestemt(
                                id = selvbestemtId,
                            ),
                    )
                val endretInntektsmelding =
                    originalInntektsmelding.copy(
                        id = UUID.randomUUID(),
                        inntekt =
                            Inntekt(
                                beloep = 1747.55,
                                inntektsdato = 22.september,
                                naturalytelser =
                                    listOf(
                                        Naturalytelse(
                                            naturalytelse = Naturalytelse.Kode.FRITRANSPORT,
                                            verdiBeloep = 10.11,
                                            sluttdato = 3.oktober,
                                        ),
                                    ),
                                endringAarsaker = emptyList(),
                            ),
                    )

                selvbestemtImRepo.lagreIm(originalInntektsmelding)
                selvbestemtImRepo.lagreIm(endretInntektsmelding)
                selvbestemtImRepo.lagreIm(mockInntektsmeldingV1())

                selvbestemtImRepo.hentNyesteIm(selvbestemtId) shouldBe endretInntektsmelding
            }

            test("henter eneste") {
                val selvbestemtId = UUID.randomUUID()
                val inntektsmelding =
                    mockInntektsmeldingV1().copy(
                        type =
                            Inntektsmelding.Type.Selvbestemt(
                                id = selvbestemtId,
                            ),
                    )

                selvbestemtImRepo.lagreIm(inntektsmelding)
                selvbestemtImRepo.lagreIm(mockInntektsmeldingV1())

                selvbestemtImRepo.hentNyesteIm(selvbestemtId) shouldBe inntektsmelding
            }

            test("gir 'null' når ingen funnet") {
                selvbestemtImRepo.lagreIm(mockInntektsmeldingV1())

                selvbestemtImRepo.hentNyesteIm(UUID.randomUUID()).shouldBeNull()
            }
        }

        context(SelvbestemtImRepo::lagreIm.name) {

            test("inntektsmeldinger lagres") {
                lesAlleRader(db) shouldHaveSize 0

                repeat(3) {
                    selvbestemtImRepo.lagreIm(mockInntektsmeldingV1())
                }

                lesAlleRader(db) shouldHaveSize 3
            }

            test("inntektsmelding- og selvbestemt ID stammer fra inntektsmelding") {
                val inntektsmelding = mockInntektsmeldingV1()

                selvbestemtImRepo.lagreIm(inntektsmelding)

                val alleRader = lesAlleRader(db)

                alleRader shouldHaveSize 1
                alleRader.first().let {
                    it[SelvbestemtInntektsmeldingEntitet.inntektsmeldingId] shouldBe inntektsmelding.id
                    it[SelvbestemtInntektsmeldingEntitet.selvbestemtId] shouldBe inntektsmelding.type.id
                    it[SelvbestemtInntektsmeldingEntitet.inntektsmelding] shouldBe inntektsmelding
                }
            }

            test("inntektsmeldinger med samme selvbestemt ID lagres") {
                val selvbestemtId = UUID.randomUUID()

                repeat(2) {
                    val inntektsmelding =
                        mockInntektsmeldingV1().copy(
                            type =
                                Inntektsmelding.Type.Selvbestemt(
                                    id = selvbestemtId,
                                ),
                        )

                    selvbestemtImRepo.lagreIm(inntektsmelding)
                }

                lesAlleRader(db) shouldHaveSize 2
            }

            test("inntektsmelding-ID må være unik") {
                val inntektsmeldingId = UUID.randomUUID()
                val inntektsmelding1 = mockInntektsmeldingV1().copy(id = inntektsmeldingId)
                val inntektsmelding2 = mockInntektsmeldingV1().copy(id = inntektsmeldingId)

                selvbestemtImRepo.lagreIm(inntektsmelding1)

                shouldThrowExactly<ExposedSQLException> {
                    selvbestemtImRepo.lagreIm(inntektsmelding2)
                }
            }
        }

        context(SelvbestemtImRepo::oppdaterJournalpostId.name) {

            test("journalpost-ID oppdateres for angitt inntektsmelding-ID (_ulik_ selvbestemt ID)") {
                val selvbestemtId = UUID.randomUUID()
                val journalpostId = randomDigitString(12)
                val inntektsmelding =
                    mockInntektsmeldingV1().copy(
                        type =
                            Inntektsmelding.Type.Selvbestemt(
                                id = selvbestemtId,
                            ),
                    )

                selvbestemtImRepo.lagreIm(inntektsmelding)
                selvbestemtImRepo.lagreIm(mockInntektsmeldingV1())
                selvbestemtImRepo.oppdaterJournalpostId(inntektsmelding.id, journalpostId)

                val alleRader = lesAlleRader(db)

                alleRader shouldHaveSize 2

                alleRader.first().let {
                    it[SelvbestemtInntektsmeldingEntitet.inntektsmeldingId] shouldBe inntektsmelding.id
                    it[SelvbestemtInntektsmeldingEntitet.selvbestemtId] shouldBe selvbestemtId
                    it[SelvbestemtInntektsmeldingEntitet.journalpostId] shouldBe journalpostId
                }

                alleRader.last().let {
                    it[SelvbestemtInntektsmeldingEntitet.inntektsmeldingId] shouldNotBe inntektsmelding.id
                    it[SelvbestemtInntektsmeldingEntitet.selvbestemtId] shouldNotBe selvbestemtId
                    it[SelvbestemtInntektsmeldingEntitet.journalpostId].shouldBeNull()
                }
            }

            test("journalpost-ID oppdateres for angitt inntektsmelding-ID (_lik_ selvbestemt ID)") {
                val selvbestemtId = UUID.randomUUID()
                val journalpostId1 = randomDigitString(5)
                val journalpostId2 = randomDigitString(6)
                val originalInntektsmelding =
                    mockInntektsmeldingV1().copy(
                        type =
                            Inntektsmelding.Type.Selvbestemt(
                                id = selvbestemtId,
                            ),
                    )
                val endretInntektsmelding =
                    originalInntektsmelding.copy(
                        id = UUID.randomUUID(),
                    )

                selvbestemtImRepo.lagreIm(originalInntektsmelding)
                selvbestemtImRepo.lagreIm(endretInntektsmelding)
                selvbestemtImRepo.oppdaterJournalpostId(originalInntektsmelding.id, journalpostId1)
                selvbestemtImRepo.oppdaterJournalpostId(endretInntektsmelding.id, journalpostId2)

                val alleRader = lesAlleRader(db)

                alleRader shouldHaveSize 2

                alleRader.first().let {
                    it[SelvbestemtInntektsmeldingEntitet.inntektsmeldingId] shouldBe originalInntektsmelding.id
                    it[SelvbestemtInntektsmeldingEntitet.selvbestemtId] shouldBe selvbestemtId
                    it[SelvbestemtInntektsmeldingEntitet.journalpostId] shouldBe journalpostId1
                }

                alleRader.last().let {
                    it[SelvbestemtInntektsmeldingEntitet.inntektsmeldingId] shouldBe endretInntektsmelding.id
                    it[SelvbestemtInntektsmeldingEntitet.selvbestemtId] shouldBe selvbestemtId
                    it[SelvbestemtInntektsmeldingEntitet.journalpostId] shouldBe journalpostId2
                }
            }

            test("oppdaterer journalpost-ID kun dersom gammel verdi er 'null'") {
                val selvbestemtId = UUID.randomUUID()
                val gammelJournalpostId = randomDigitString(10)
                val nyJournalpostId = randomDigitString(8)
                val originalInntektsmelding =
                    mockInntektsmeldingV1().copy(
                        type =
                            Inntektsmelding.Type.Selvbestemt(
                                id = selvbestemtId,
                            ),
                    )
                val endretInntektsmelding =
                    originalInntektsmelding.copy(
                        id = UUID.randomUUID(),
                    )

                selvbestemtImRepo.lagreIm(originalInntektsmelding)
                selvbestemtImRepo.lagreIm(endretInntektsmelding)
                selvbestemtImRepo.oppdaterJournalpostId(endretInntektsmelding.id, gammelJournalpostId)

                val alleRaderEtterSetup = lesAlleRader(db)

                alleRaderEtterSetup shouldHaveSize 2

                alleRaderEtterSetup.first().let {
                    it[SelvbestemtInntektsmeldingEntitet.inntektsmeldingId] shouldBe originalInntektsmelding.id
                    it[SelvbestemtInntektsmeldingEntitet.selvbestemtId] shouldBe selvbestemtId
                    it[SelvbestemtInntektsmeldingEntitet.journalpostId].shouldBeNull()
                }

                alleRaderEtterSetup.last().let {
                    it[SelvbestemtInntektsmeldingEntitet.inntektsmeldingId] shouldBe endretInntektsmelding.id
                    it[SelvbestemtInntektsmeldingEntitet.selvbestemtId] shouldBe selvbestemtId
                    it[SelvbestemtInntektsmeldingEntitet.journalpostId] shouldBe gammelJournalpostId
                }

                // Denne skal ha ingen effekt
                selvbestemtImRepo.oppdaterJournalpostId(endretInntektsmelding.id, nyJournalpostId)

                val alleRaderEtterOppdatering = lesAlleRader(db)

                alleRaderEtterOppdatering shouldHaveSize 2

                alleRaderEtterSetup.first().let {
                    it[SelvbestemtInntektsmeldingEntitet.inntektsmeldingId] shouldBe originalInntektsmelding.id
                    it[SelvbestemtInntektsmeldingEntitet.selvbestemtId] shouldBe selvbestemtId
                    it[SelvbestemtInntektsmeldingEntitet.journalpostId].shouldBeNull()
                }

                alleRaderEtterSetup.last().let {
                    it[SelvbestemtInntektsmeldingEntitet.inntektsmeldingId] shouldBe endretInntektsmelding.id
                    it[SelvbestemtInntektsmeldingEntitet.selvbestemtId] shouldBe selvbestemtId
                    it[SelvbestemtInntektsmeldingEntitet.journalpostId] shouldBe gammelJournalpostId
                }
            }

            test("kaster exception dersom journalpost-ID ikke er unik") {
                val journalpostId = randomDigitString(20)
                val inntektsmelding1 = mockInntektsmeldingV1()
                val inntektsmelding2 = mockInntektsmeldingV1()

                selvbestemtImRepo.lagreIm(inntektsmelding1)
                selvbestemtImRepo.lagreIm(inntektsmelding2)

                selvbestemtImRepo.oppdaterJournalpostId(inntektsmelding1.id, journalpostId)

                shouldThrowExactly<ExposedSQLException> {
                    selvbestemtImRepo.oppdaterJournalpostId(inntektsmelding2.id, journalpostId)
                }
            }

            test("krasjer ikke ved ingen matchende rader") {
                shouldNotThrowAny {
                    selvbestemtImRepo.oppdaterJournalpostId(UUID.randomUUID(), randomDigitString(7))
                }
            }
        }

        context(InntektsmeldingRepository::oppdaterSomProsessert.name) {
            test("skal oppdatere im som prosessert") {
                val inntektsmelding = mockInntektsmeldingV1().copy(type = Inntektsmelding.Type.Selvbestemt(UUID.randomUUID()))
                val inntektsmeldingIkkeProsessert = mockInntektsmeldingV1().copy(type = Inntektsmelding.Type.Selvbestemt(UUID.randomUUID()))

                selvbestemtImRepo.lagreIm(inntektsmelding)
                delay(1.seconds)
                selvbestemtImRepo.lagreIm(inntektsmeldingIkkeProsessert)

                selvbestemtImRepo.oppdaterSomProsessert(inntektsmelding.id)

                val resultat =
                    transaction(db) {
                        SelvbestemtInntektsmeldingEntitet
                            .selectAll()
                            .orderBy(SelvbestemtInntektsmeldingEntitet.opprettet)
                            .toList()
                    }

                resultat shouldHaveSize 2

                val prosessertVindu =
                    LocalDateTime.now().let {
                        it.minusMinutes(1)..it.plusMinutes(1)
                    }

                SelvbestemtInntektsmeldingEntitet.apply {
                    resultat[0][opprettet] shouldBeLessThan resultat[1][opprettet]

                    resultat[0][this.inntektsmelding] shouldBe inntektsmelding
                    resultat[0][prosessert].shouldNotBeNull().shouldBeIn(prosessertVindu)

                    resultat[1][this.inntektsmelding].shouldNotBeNull()
                    resultat[1][prosessert].shouldBeNull()
                }
            }
        }
    })

private fun lesAlleRader(db: Database): List<ResultRow> =
    transaction(db) {
        SelvbestemtInntektsmeldingEntitet
            .selectAll()
            .orderBy(SelvbestemtInntektsmeldingEntitet.opprettet)
            .toList()
    }
