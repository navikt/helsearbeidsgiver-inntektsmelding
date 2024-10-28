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
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.Paaminnelse
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.NotifikasjonTekst
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.sakLevetid
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.juli
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class OpprettForespoerselSakOgOppgaveRiverTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockAgNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>()

        OpprettForespoerselSakOgOppgaveRiver(
            lenkeBaseUrl = "en-slags-url",
            agNotifikasjonKlient = mockAgNotifikasjonKlient,
            paaminnelseAktivert = true,
            tidMellomOppgaveopprettelseOgPaaminnelse = "P10M",
        ).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
        }

        fun innkommendeMelding(): OpprettForespoerselSakOgOppgaveMelding =
            OpprettForespoerselSakOgOppgaveMelding(
                eventName = EventName.SAK_OG_OPPGAVE_OPPRETT_REQUESTED,
                transaksjonId = UUID.randomUUID(),
                forespoerselId = UUID.randomUUID(),
                orgnr = Orgnr.genererGyldig(),
                sykmeldt =
                    Person(
                        fnr = Fnr.genererGyldig(),
                        navn = "Peer Gynt",
                        foedselsdato = 12.juli,
                    ),
                orgNavn = "Peer Gynts Løgn og Bedrageri LTD",
                skalHaPaaminnelse = true,
            )

        fun forventetFail(innkommendeMelding: OpprettForespoerselSakOgOppgaveMelding): Fail =
            Fail(
                feilmelding = "Klarte ikke opprette sak og/eller oppgave for forespurt inntektmelding.",
                event = innkommendeMelding.eventName,
                transaksjonId = innkommendeMelding.transaksjonId,
                forespoerselId = innkommendeMelding.forespoerselId,
                utloesendeMelding = innkommendeMelding.toMap().toJson(),
            )

        test("oppretter sak og oppgave") {
            coEvery {
                mockAgNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any())
            } returns MOCK_SAK_ID

            coEvery {
                mockAgNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns MOCK_OPPGAVE_ID

            val innkommendeMelding = innkommendeMelding()

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.SAK_OG_OPPGAVE_OPPRETTET.toJson(),
                    Key.UUID to innkommendeMelding.transaksjonId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FORESPOERSEL_ID to innkommendeMelding.forespoerselId.toJson(),
                            Key.SAK_ID to MOCK_SAK_ID.toJson(),
                            Key.OPPGAVE_ID to MOCK_OPPGAVE_ID.toJson(),
                        ).toJson(),
                )

            coVerifySequence {
                mockAgNotifikasjonKlient.opprettNySak(
                    virksomhetsnummer = innkommendeMelding.orgnr.verdi,
                    grupperingsid = innkommendeMelding.forespoerselId.toString(),
                    merkelapp = NotifikasjonTekst.MERKELAPP,
                    lenke = "en-slags-url/im-dialog/${innkommendeMelding.forespoerselId}",
                    tittel = NotifikasjonTekst.sakTittel(innkommendeMelding.sykmeldt),
                    statusTekst = NotifikasjonTekst.STATUS_TEKST_UNDER_BEHANDLING,
                    initiellStatus = SaksStatus.UNDER_BEHANDLING,
                    harddeleteOm = sakLevetid,
                )
                mockAgNotifikasjonKlient.opprettNyOppgave(
                    virksomhetsnummer = innkommendeMelding.orgnr.verdi,
                    eksternId = innkommendeMelding.forespoerselId.toString(),
                    grupperingsid = innkommendeMelding.forespoerselId.toString(),
                    merkelapp = NotifikasjonTekst.MERKELAPP,
                    lenke = "en-slags-url/im-dialog/${innkommendeMelding.forespoerselId}",
                    tekst = NotifikasjonTekst.OPPGAVE_TEKST,
                    varslingTittel = NotifikasjonTekst.STATUS_TEKST_UNDER_BEHANDLING,
                    varslingInnhold = NotifikasjonTekst.oppgaveInnhold(innkommendeMelding.orgnr, innkommendeMelding.orgNavn),
                    tidspunkt = null,
                    paaminnelse =
                        Paaminnelse(
                            tittel = "Påminnelse: ${NotifikasjonTekst.STATUS_TEKST_UNDER_BEHANDLING}",
                            innhold = NotifikasjonTekst.purringInnhold(innkommendeMelding.orgnr, innkommendeMelding.orgNavn),
                            tidMellomOppgaveopprettelseOgPaaminnelse = "P10M",
                        ),
                )
            }
        }

        test("ukjent feil for sak håndteres") {
            val innkommendeMelding = innkommendeMelding()

            coEvery {
                mockAgNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any())
            } throws NullPointerException("To me, religion is like Paul Rudd.")

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail(innkommendeMelding).tilMelding()

            coVerifySequence {
                mockAgNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

        test("ukjent feil for oppgave håndteres") {
            val innkommendeMelding = innkommendeMelding()

            coEvery {
                mockAgNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any())
            } returns MOCK_SAK_ID

            coEvery {
                mockAgNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } throws NullPointerException("Doing anything more than the minimum amount of work required is my definition of failing.")

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail(innkommendeMelding).tilMelding()

            coVerifySequence {
                mockAgNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any())
                mockAgNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

        context("ignorerer melding") {
            withData(
                mapOf(
                    "melding med behov" to Pair(Key.BEHOV, BehovType.LAGRE_SELVBESTEMT_IM.toJson()),
                    "melding med data som flagg" to Pair(Key.DATA, "".toJson()),
                    "melding med fail" to Pair(Key.FAIL, forventetFail(innkommendeMelding()).toJson(Fail.serializer())),
                ),
            ) { uoensketKeyMedVerdi ->
                testRapid.sendJson(
                    innkommendeMelding()
                        .toMap()
                        .plus(uoensketKeyMedVerdi),
                )

                testRapid.inspektør.size shouldBeExactly 0

                coVerify(exactly = 0) {
                    mockAgNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any())
                    mockAgNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
                }
            }
        }
    })

private const val MOCK_SAK_ID = "en enestående særegen sak-id"
private const val MOCK_OPPGAVE_ID = "en makalaus unik oppgave-id"

private fun OpprettForespoerselSakOgOppgaveMelding.toMap() =
    mapOf(
        Key.EVENT_NAME to eventName.toJson(),
        Key.UUID to transaksjonId.toJson(),
        Key.DATA to
            mapOf(
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.ORGNRUNDERENHET to orgnr.toJson(),
                Key.SYKMELDT to sykmeldt.toJson(Person.serializer()),
                Key.VIRKSOMHET to orgNavn.toJson(),
                Key.SKAL_HA_PAAMINNELSE to skalHaPaaminnelse.toJson(Boolean.serializer()),
            ).toJson(),
    )
