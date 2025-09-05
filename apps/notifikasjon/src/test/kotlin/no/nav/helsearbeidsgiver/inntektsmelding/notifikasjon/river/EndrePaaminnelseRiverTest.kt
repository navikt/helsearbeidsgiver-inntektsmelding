package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerifySequence
import io.mockk.just
import io.mockk.mockk
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.utils.kontrakt.domene.forespoersel.test.mockForespoersel
import no.nav.hag.simba.utils.rr.test.firstMessage
import no.nav.hag.simba.utils.rr.test.mockConnectToRapid
import no.nav.hag.simba.utils.rr.test.sendJson
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.OppgaveAlleredeUtfoertException
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.OppgaveEndrePaaminnelseByEksternIdException
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.Paaminnelse
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.SakEllerOppgaveFinnesIkkeException
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.NotifikasjonTekst
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class EndrePaaminnelseRiverTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockagNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>()

        mockConnectToRapid(testRapid) {
            listOf(
                EndrePaaminnelseRiver(mockagNotifikasjonKlient),
            )
        }

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        test("endre påminnelse for forespørsel") {
            val innkommendeMelding = innkommendeEndrePaaminnelseMelding()

            coEvery { mockagNotifikasjonKlient.endreOppgavePaaminnelserByEksternId(any(), any(), any()) } just Runs

            testRapid.sendJson(innkommendeMelding.toMap())

            coVerifySequence {
                mockagNotifikasjonKlient.endreOppgavePaaminnelserByEksternId(
                    NotifikasjonTekst.MERKELAPP,
                    innkommendeMelding.forespoerselId.toString(),
                    EndrePaaminnelseMock.paaminnelse,
                )
            }

            testRapid.inspektør.size shouldBeExactly 0
        }

        test("ukjent feil ved endring av påminnelser håndteres") {
            val innkommendeMelding = innkommendeEndrePaaminnelseMelding()

            coEvery { mockagNotifikasjonKlient.endreOppgavePaaminnelserByEksternId(any(), any(), any()) } throws
                OppgaveEndrePaaminnelseByEksternIdException(
                    innkommendeMelding.forespoerselId.toString(),
                    "mock feil",
                )

            testRapid.sendJson(innkommendeMelding.toMap())

            coVerifySequence {
                mockagNotifikasjonKlient.endreOppgavePaaminnelserByEksternId(
                    NotifikasjonTekst.MERKELAPP,
                    innkommendeMelding.forespoerselId.toString(),
                    EndrePaaminnelseMock.paaminnelse,
                )
            }

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail(innkommendeMelding).tilMelding()
        }

        test("kaster ingen exception dersom oppgaven ikke finnes") {
            val innkommendeMelding = innkommendeEndrePaaminnelseMelding()

            coEvery {
                mockagNotifikasjonKlient.endreOppgavePaaminnelserByEksternId(
                    any(),
                    any(),
                    any(),
                )
            } throws SakEllerOppgaveFinnesIkkeException("Sak eller oppgave finnes ikke.")

            testRapid.sendJson(innkommendeMelding.toMap())

            coVerifySequence {
                mockagNotifikasjonKlient.endreOppgavePaaminnelserByEksternId(
                    NotifikasjonTekst.MERKELAPP,
                    innkommendeMelding.forespoerselId.toString(),
                    EndrePaaminnelseMock.paaminnelse,
                )
            }

            testRapid.inspektør.size shouldBeExactly 0
        }

        test("kaster ikke exception dersom oppgaven allerede er utført") {
            val innkommendeMelding = innkommendeEndrePaaminnelseMelding()

            coEvery { mockagNotifikasjonKlient.endreOppgavePaaminnelserByEksternId(any(), any(), any()) } throws
                OppgaveAlleredeUtfoertException("Oppgave er allerede utfoert")

            testRapid.sendJson(innkommendeMelding.toMap())

            coVerifySequence {
                mockagNotifikasjonKlient.endreOppgavePaaminnelserByEksternId(
                    NotifikasjonTekst.MERKELAPP,
                    innkommendeMelding.forespoerselId.toString(),
                    EndrePaaminnelseMock.paaminnelse,
                )
            }

            testRapid.inspektør.size shouldBeExactly 0
        }
    })

object EndrePaaminnelseMock {
    val forespoersel = mockForespoersel()
    val orgNavn = "mock orgnavn"
    val paaminnelse =
        Paaminnelse(
            NotifikasjonTekst.PAAMINNELSE_TITTEL,
            NotifikasjonTekst.paaminnelseInnhold(
                orgnr = forespoersel.orgnr,
                orgNavn = orgNavn,
                sykmeldingsperioder = forespoersel.sykmeldingsperioder,
            ),
            tidMellomOppgaveopprettelseOgPaaminnelse = "P28D",
        )
}

private fun forventetFail(innkommendeMelding: EndrePaaminnelseMelding): Fail =
    Fail(
        feilmelding = "Klarte ikke endre påminnelse på oppgave.",
        kontekstId = innkommendeMelding.kontekstId,
        utloesendeMelding = innkommendeMelding.toMap(),
    )

private fun EndrePaaminnelseMelding.toMap() =
    mapOf(
        Key.EVENT_NAME to eventName.toJson(),
        Key.KONTEKST_ID to kontekstId.toJson(),
        Key.DATA to
            mapOf(
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.FORESPOERSEL to forespoersel.toJson(Forespoersel.serializer()),
                Key.VIRKSOMHET to orgNavn.toJson(),
            ).toJson(),
    )

fun innkommendeEndrePaaminnelseMelding(): EndrePaaminnelseMelding =
    EndrePaaminnelseMelding(
        eventName = EventName.OPPGAVE_ENDRE_PAAMINNELSE_REQUESTED,
        kontekstId = UUID.randomUUID(),
        forespoerselId = UUID.randomUUID(),
        forespoersel = EndrePaaminnelseMock.forespoersel,
        orgNavn = EndrePaaminnelseMock.orgNavn,
    )
