package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.OpprettNyOppgaveException
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.metrics.recordTime
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.utils.simpleName
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import java.util.UUID

class OpprettOppgaveLoeser(
    rapidsConnection: RapidsConnection,
    private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val linkUrl: String
) : Loeser(rapidsConnection) {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandValue(Key.BEHOV.str, BehovType.OPPRETT_OPPGAVE.name)
            it.requireKey(Key.ORGNRUNDERENHET.str)
            it.requireKey(Key.VIRKSOMHET.str)
        }

    override fun onBehov(behov: Behov) {
        val utloesendeMelding = behov.jsonMessage.toJson().parseJson()
        val json = utloesendeMelding.toMap()

        val transaksjonId = Key.UUID.lesOrNull(UuidSerializer, json)
            .orDefault {
                UUID.randomUUID().also {
                    sikkerLogger.error(
                        "Mangler transaksjonId i ${simpleName()}. Erstatter med ny, tilfeldig UUID '$it'." +
                            "\n${utloesendeMelding.toPretty()}"
                    )
                }
            }

        val orgnr = Key.ORGNRUNDERENHET.les(String.serializer(), json)
        val virksomhetNavn = Key.VIRKSOMHET.les(String.serializer(), json)

        val forespoerselId = behov.forespoerselId?.let(UUID::fromString)
        if (forespoerselId == null) {
            val fail = Fail(
                feilmelding = "Mangler forespørselId",
                event = behov.event,
                transaksjonId = transaksjonId,
                forespoerselId = null,
                utloesendeMelding = utloesendeMelding
            )
            publishFail(fail)
            return
        }

        val oppgaveId = opprettOppgave(forespoerselId, orgnr, virksomhetNavn)
        if (oppgaveId.isNullOrBlank()) {
            val fail = Fail(
                feilmelding = "Feilet ved opprett oppgave",
                event = behov.event,
                transaksjonId = transaksjonId,
                forespoerselId = forespoerselId,
                utloesendeMelding = utloesendeMelding
            )
            publishFail(fail)
        } else {
            rapidsConnection.publish(
                Key.EVENT_NAME to behov.event.toJson(),
                Key.BEHOV to BehovType.PERSISTER_OPPGAVE_ID.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.ORGNRUNDERENHET to orgnr.toJson(),
                Key.OPPGAVE_ID to oppgaveId.toJson()
            )
                .also {
                    logger.info("Publiserte behov for '${behov.event}' med transaksjonId '$transaksjonId'.")
                    sikkerLogger.info("Publiserte behov:\n${it.toPretty()}")
                }

            sikkerLogger.info("OpprettOppgaveLøser publiserte med transaksjonId: $transaksjonId")
        }
    }

    private fun opprettOppgave(
        forespoerselId: UUID,
        orgnr: String,
        virksomhetnavn: String
    ): String? {
        return try {
            Metrics.agNotifikasjonRequest.recordTime(arbeidsgiverNotifikasjonKlient::opprettNyOppgave) {
                arbeidsgiverNotifikasjonKlient.opprettNyOppgave(
                    eksternId = forespoerselId.toString(),
                    lenke = "$linkUrl/im-dialog/$forespoerselId",
                    tekst = "Send inn inntektsmelding",
                    virksomhetsnummer = orgnr,
                    merkelapp = "Inntektsmelding",
                    tidspunkt = null,
                    grupperingsid = forespoerselId.toString(),
                    varslingTittel = "Nav trenger inntektsmelding",
                    varslingInnhold = """$virksomhetnavn - orgnr $orgnr: En av dine ansatte har søkt om sykepenger
                    og vi trenger inntektsmelding for å behandle søknaden.
                    Logg inn på Min side – arbeidsgiver hos NAV.
                    Hvis dere sender inntektsmelding via lønnssystem kan dere fortsatt gjøre dette,
                    og trenger ikke sende inn via Min side – arbeidsgiver."""
                )
            }
        } catch (e: OpprettNyOppgaveException) {
            sikkerLogger.error("Feil ved kall til opprett oppgave for $forespoerselId!", e)
            logger.error("Feil ved kall til opprett oppgave for $forespoerselId!")
            return null
        }
    }
}
