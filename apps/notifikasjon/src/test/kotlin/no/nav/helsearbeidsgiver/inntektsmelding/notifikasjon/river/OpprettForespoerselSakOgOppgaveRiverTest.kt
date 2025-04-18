package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.Paaminnelse
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.SakEllerOppgaveDuplikatException
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespoersel
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.NotifikasjonTekst
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.sakLevetid
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class OpprettForespoerselSakOgOppgaveRiverTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockAgNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>()

        OpprettForespoerselSakOgOppgaveRiver(
            lenkeBaseUrl = "en-slags-url",
            tidMellomOppgaveOpprettelseOgPaaminnelse = "P28D",
            agNotifikasjonKlient = mockAgNotifikasjonKlient,
        ).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        test("oppretter sak og oppgave") {
            val sakId = UUID.randomUUID().toString()
            val oppgaveId = UUID.randomUUID().toString()

            coEvery { mockAgNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns sakId
            coEvery { mockAgNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns oppgaveId

            val innkommendeMelding = innkommendeOpprettForespoerselSakOgOppgaveMelding()

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetUtgaaendeMelding(innkommendeMelding, sakId, oppgaveId)

            coVerifySequence {
                mockAgNotifikasjonKlient.opprettNySak(
                    virksomhetsnummer = innkommendeMelding.forespoersel.orgnr.verdi,
                    grupperingsid = innkommendeMelding.forespoerselId.toString(),
                    merkelapp = NotifikasjonTekst.MERKELAPP,
                    lenke = "en-slags-url/im-dialog/${innkommendeMelding.forespoerselId}",
                    tittel = NotifikasjonTekst.sakTittel(innkommendeMelding.sykmeldt),
                    statusTekst = NotifikasjonTekst.STATUS_TEKST_UNDER_BEHANDLING,
                    tilleggsinfo = NotifikasjonTekst.sakTilleggsinfo(innkommendeMelding.forespoersel.sykmeldingsperioder),
                    initiellStatus = SaksStatus.UNDER_BEHANDLING,
                    hardDeleteOm = sakLevetid,
                )
                mockAgNotifikasjonKlient.opprettNyOppgave(
                    virksomhetsnummer = innkommendeMelding.forespoersel.orgnr.verdi,
                    eksternId = innkommendeMelding.forespoerselId.toString(),
                    grupperingsid = innkommendeMelding.forespoerselId.toString(),
                    merkelapp = NotifikasjonTekst.MERKELAPP,
                    lenke = "en-slags-url/im-dialog/${innkommendeMelding.forespoerselId}",
                    tekst = NotifikasjonTekst.OPPGAVE_TEKST,
                    varslingTittel = NotifikasjonTekst.STATUS_TEKST_UNDER_BEHANDLING,
                    varslingInnhold =
                        NotifikasjonTekst.oppgaveInnhold(
                            innkommendeMelding.forespoersel.orgnr,
                            innkommendeMelding.orgNavn,
                            innkommendeMelding.forespoersel.sykmeldingsperioder,
                        ),
                    tidspunkt = null,
                    paaminnelse =
                        Paaminnelse(
                            tittel = NotifikasjonTekst.PAAMINNELSE_TITTEL,
                            innhold =
                                NotifikasjonTekst.paaminnelseInnhold(
                                    innkommendeMelding.forespoersel.orgnr,
                                    innkommendeMelding.orgNavn,
                                    innkommendeMelding.forespoersel.sykmeldingsperioder,
                                ),
                            tidMellomOppgaveopprettelseOgPaaminnelse = "P28D",
                        ),
                )
            }
        }

        test("sak opprettes selv om oppgave har duplikat") {
            val sakId = UUID.randomUUID().toString()
            val duplikatOppgaveId = UUID.randomUUID().toString()

            coEvery { mockAgNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns sakId

            coEvery {
                mockAgNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } throws SakEllerOppgaveDuplikatException(duplikatOppgaveId, "mock feilmelding")

            val innkommendeMelding = innkommendeOpprettForespoerselSakOgOppgaveMelding()

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetUtgaaendeMelding(innkommendeMelding, sakId, duplikatOppgaveId)

            coVerifySequence {
                mockAgNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any(), any())
                mockAgNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

        test("oppgave opprettes selv om sak har duplikat") {
            val duplikatSakId = UUID.randomUUID().toString()
            val oppgaveId = UUID.randomUUID().toString()

            coEvery {
                mockAgNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } throws SakEllerOppgaveDuplikatException(duplikatSakId, "mock feilmelding")

            coEvery { mockAgNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns oppgaveId

            val innkommendeMelding = innkommendeOpprettForespoerselSakOgOppgaveMelding()

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetUtgaaendeMelding(innkommendeMelding, duplikatSakId, oppgaveId)

            coVerifySequence {
                mockAgNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any(), any())
                mockAgNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

        test("sak og oppgave som har duplikat håndteres") {
            val duplikatSakId = UUID.randomUUID().toString()
            val duplikatOppgaveId = UUID.randomUUID().toString()

            coEvery {
                mockAgNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } throws SakEllerOppgaveDuplikatException(duplikatSakId, "mock feilmelding")

            coEvery {
                mockAgNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } throws SakEllerOppgaveDuplikatException(duplikatOppgaveId, "mock feilmelding")

            val innkommendeMelding = innkommendeOpprettForespoerselSakOgOppgaveMelding()

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetUtgaaendeMelding(innkommendeMelding, duplikatSakId, duplikatOppgaveId)

            coVerifySequence {
                mockAgNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any(), any())
                mockAgNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

        test("ukjent feil for sak håndteres") {
            val innkommendeMelding = innkommendeOpprettForespoerselSakOgOppgaveMelding()

            coEvery {
                mockAgNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } throws NullPointerException("To me, religion is like Paul Rudd.")

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail(innkommendeMelding).tilMelding()

            coVerifySequence {
                mockAgNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

        test("ukjent feil for oppgave håndteres") {
            val innkommendeMelding = innkommendeOpprettForespoerselSakOgOppgaveMelding()

            coEvery {
                mockAgNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns UUID.randomUUID().toString()

            coEvery {
                mockAgNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } throws NullPointerException("Doing anything more than the minimum amount of work required is my definition of failing.")

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail(innkommendeMelding).tilMelding()

            coVerifySequence {
                mockAgNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any(), any())
                mockAgNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med behov" to Pair(Key.BEHOV, BehovType.LAGRE_SELVBESTEMT_IM.toJson()),
                    "melding med data som flagg" to Pair(Key.DATA, "".toJson()),
                    "melding med fail" to Pair(Key.FAIL, forventetFail(innkommendeOpprettForespoerselSakOgOppgaveMelding()).toJson(Fail.serializer())),
                ),
            ) { uoensketKeyMedVerdi ->
                testRapid.sendJson(
                    innkommendeOpprettForespoerselSakOgOppgaveMelding()
                        .toMap()
                        .plus(uoensketKeyMedVerdi),
                )

                testRapid.inspektør.size shouldBeExactly 0

                coVerify(exactly = 0) {
                    mockAgNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any(), any())
                    mockAgNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
                }
            }
        }
    })

fun innkommendeOpprettForespoerselSakOgOppgaveMelding(): OpprettForespoerselSakOgOppgaveMelding {
    val forespoersel = mockForespoersel()
    return OpprettForespoerselSakOgOppgaveMelding(
        eventName = EventName.SAK_OG_OPPGAVE_OPPRETT_REQUESTED,
        kontekstId = UUID.randomUUID(),
        forespoerselId = UUID.randomUUID(),
        sykmeldt = Person(forespoersel.fnr, "Peer Gynt"),
        orgNavn = "Peer Gynts Løgn og Bedrageri LTD",
        skalHaPaaminnelse = true,
        forespoersel = forespoersel,
    )
}

private fun OpprettForespoerselSakOgOppgaveMelding.toMap() =
    mapOf(
        Key.EVENT_NAME to eventName.toJson(),
        Key.KONTEKST_ID to kontekstId.toJson(),
        Key.DATA to
            mapOf(
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.SYKMELDT to sykmeldt.toJson(Person.serializer()),
                Key.VIRKSOMHET to orgNavn.toJson(),
                Key.SKAL_HA_PAAMINNELSE to skalHaPaaminnelse.toJson(Boolean.serializer()),
                Key.FORESPOERSEL to forespoersel.toJson(Forespoersel.serializer()),
            ).toJson(),
    )

private fun forventetUtgaaendeMelding(
    innkommendeMelding: OpprettForespoerselSakOgOppgaveMelding,
    sakId: String,
    oppgaveId: String,
): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to EventName.SAK_OG_OPPGAVE_OPPRETTET.toJson(),
        Key.KONTEKST_ID to innkommendeMelding.kontekstId.toJson(),
        Key.DATA to
            mapOf(
                Key.FORESPOERSEL_ID to innkommendeMelding.forespoerselId.toJson(),
                Key.SAK_ID to sakId.toJson(),
                Key.OPPGAVE_ID to oppgaveId.toJson(),
            ).toJson(),
    )

private fun forventetFail(innkommendeMelding: OpprettForespoerselSakOgOppgaveMelding): Fail =
    Fail(
        feilmelding = "Klarte ikke opprette sak og/eller oppgave for forespurt inntektmelding.",
        kontekstId = innkommendeMelding.kontekstId,
        utloesendeMelding = innkommendeMelding.toMap(),
    )
