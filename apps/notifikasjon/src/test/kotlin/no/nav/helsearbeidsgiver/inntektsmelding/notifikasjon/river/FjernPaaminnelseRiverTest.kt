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
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.OppgaveEndrePaaminnelseByEksternIdException
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.SakEllerOppgaveFinnesIkkeException
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.NotifikasjonTekst
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.PaaminnelseToggle
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class FjernPaaminnelseRiverTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockagNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>()

        val mockPaaminnelseToggle =
            PaaminnelseToggle(
                oppgavePaaminnelseAktivert = true,
                tidMellomOppgaveopprettelseOgPaaminnelse = "P28D",
            )

        FjernPaaminnelseRiver(
            agNotifikasjonKlient = mockagNotifikasjonKlient,
            paaminnelseToggle = mockPaaminnelseToggle,
        ).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        test("fjern påminnelse for forespørsel") {
            val innkommendeMelding = innkommendeFjernPaaminnelseMelding()

            coEvery { mockagNotifikasjonKlient.slettOppgavePaaminnelserByEksternId(any(), any()) } just Runs

            testRapid.sendJson(innkommendeMelding.toMap())

            coVerifySequence {
                mockagNotifikasjonKlient.slettOppgavePaaminnelserByEksternId(
                    NotifikasjonTekst.MERKELAPP,
                    innkommendeMelding.forespoerselId.toString(),
                )
            }

            testRapid.inspektør.size shouldBeExactly 0
        }

        test("ukjent feil ved sletting av påminnelser håndteres") {
            val innkommendeMelding = innkommendeFjernPaaminnelseMelding()

            coEvery { mockagNotifikasjonKlient.slettOppgavePaaminnelserByEksternId(any(), any()) } throws
                OppgaveEndrePaaminnelseByEksternIdException(
                    innkommendeMelding.forespoerselId.toString(),
                    "mock feil",
                )

            testRapid.sendJson(innkommendeMelding.toMap())

            coVerifySequence {
                mockagNotifikasjonKlient.slettOppgavePaaminnelserByEksternId(
                    NotifikasjonTekst.MERKELAPP,
                    innkommendeMelding.forespoerselId.toString(),
                )
            }

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail(innkommendeMelding).tilMelding()
        }

        test("kaster ingen exception dersom oppgaven ikke finnes") {
            val innkommendeMelding = innkommendeFjernPaaminnelseMelding()

            coEvery {
                mockagNotifikasjonKlient.slettOppgavePaaminnelserByEksternId(
                    any(),
                    any(),
                )
            } throws SakEllerOppgaveFinnesIkkeException("Sak eller oppgave finnes ikke.")

            testRapid.sendJson(innkommendeMelding.toMap())

            coVerifySequence {
                mockagNotifikasjonKlient.slettOppgavePaaminnelserByEksternId(
                    NotifikasjonTekst.MERKELAPP,
                    innkommendeMelding.forespoerselId.toString(),
                )
            }

            testRapid.inspektør.size shouldBeExactly 0
        }

        test("kaster ikke exception dersom oppgaven allerede er utført") {
            val innkommendeMelding = innkommendeFjernPaaminnelseMelding()

            coEvery { mockagNotifikasjonKlient.slettOppgavePaaminnelserByEksternId(any(), any()) } just Runs

            testRapid.sendJson(innkommendeMelding.toMap())

            coVerifySequence {
                mockagNotifikasjonKlient.slettOppgavePaaminnelserByEksternId(
                    NotifikasjonTekst.MERKELAPP,
                    innkommendeMelding.forespoerselId.toString(),
                )
            }

            testRapid.inspektør.size shouldBeExactly 0
        }
    })

private fun forventetFail(innkommendeMelding: FjernPaaminnelseMelding): Fail =
    Fail(
        feilmelding = "Klarte ikke fjerne påminnelse fra oppgave.",
        kontekstId = innkommendeMelding.kontekstId,
        utloesendeMelding = innkommendeMelding.toMap(),
    )

private fun FjernPaaminnelseMelding.toMap() =
    mapOf(
        Key.EVENT_NAME to eventName.toJson(),
        Key.KONTEKST_ID to kontekstId.toJson(),
        Key.FORESPOERSEL_ID to forespoerselId.toJson(),
    )

fun innkommendeFjernPaaminnelseMelding(): FjernPaaminnelseMelding =
    FjernPaaminnelseMelding(
        eventName = EventName.FORESPOERSEL_KASTET_TIL_INFOTRYGD,
        kontekstId = UUID.randomUUID(),
        forespoerselId = UUID.randomUUID(),
    )
