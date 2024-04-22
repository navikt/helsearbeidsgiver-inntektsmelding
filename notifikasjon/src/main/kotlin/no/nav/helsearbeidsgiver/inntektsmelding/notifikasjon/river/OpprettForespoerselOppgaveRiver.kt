package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Orgnr
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.loeser.ObjectRiver
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.metrics.recordTime
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.db.ForespoerselOppgaveRepo
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class OpprettForespoerselOppgaveMelding(
    val eventName: EventName,
    val transaksjonId: UUID,
    val forespoerselId: UUID,
    val orgnr: Orgnr,
    val orgNavn: String
)

// TODO test
class OpprettForespoerselOppgaveRiver(
    private val lenkeBaseUrl: String,
    private val agNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val forespoerselOppgaveRepo: ForespoerselOppgaveRepo
) : ObjectRiver<OpprettForespoerselOppgaveMelding>() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): OpprettForespoerselOppgaveMelding? =
        if (setOf(Key.BEHOV, Key.DATA, Key.FAIL).any(json::containsKey)) {
            null
        } else {
            OpprettForespoerselOppgaveMelding(
                eventName = Key.EVENT_NAME.krev(EventName.OPPGAVE_OPPRETT_REQUESTED, EventName.serializer(), json),
                transaksjonId = Key.UUID.les(UuidSerializer, json),
                forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, json),
                orgnr = Key.ORGNRUNDERENHET.les(Orgnr.serializer(), json),
                orgNavn = Key.VIRKSOMHET.les(String.serializer(), json)
            )
        }

    override fun OpprettForespoerselOppgaveMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val oppgaveId = opprettOppgave(forespoerselId, orgnr, orgNavn)

        return MdcUtils.withLogFields(
            Log.oppgaveId(oppgaveId)
        ) {
            forespoerselOppgaveRepo.lagreOppgaveId(forespoerselId, oppgaveId)

            mapOf(
                Key.EVENT_NAME to EventName.OPPGAVE_OPPRETTET.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.OPPGAVE_ID to oppgaveId.toJson()
            )
        }
    }

    override fun OpprettForespoerselOppgaveMelding.haandterFeil(json: Map<Key, JsonElement>, error: Throwable): Map<Key, JsonElement> {
        val fail = Fail(
            feilmelding = "Klarte ikke opprette/lagre oppgave for forespurt inntektmelding.",
            event = eventName,
            transaksjonId = transaksjonId,
            forespoerselId = forespoerselId,
            utloesendeMelding = json.toJson()
        )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun OpprettForespoerselOppgaveMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@OpprettForespoerselOppgaveRiver),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId)
        )

    private fun opprettOppgave(
        forespoerselId: UUID,
        orgnr: Orgnr,
        orgNavn: String
    ): String {
        val varslingInnhold = """
            $orgNavn – orgnr $orgnr: En av dine ansatte har søkt om sykepenger
            og vi trenger inntektsmelding for å behandle søknaden.
            Logg inn på Min side – arbeidsgiver hos NAV.
            Hvis dere sender inntektsmelding via lønnssystem kan dere fortsatt gjøre dette,
            og trenger ikke sende inn via Min side – arbeidsgiver.
        """

        return Metrics.agNotifikasjonRequest.recordTime(agNotifikasjonKlient::opprettNyOppgave) {
            agNotifikasjonKlient.opprettNyOppgave(
                virksomhetsnummer = orgnr.verdi,
                merkelapp = "Inntektsmelding",
                grupperingsid = forespoerselId.toString(),
                eksternId = forespoerselId.toString(),
                lenke = "$lenkeBaseUrl/im-dialog/$forespoerselId",
                tekst = "Send inn inntektsmelding",
                varslingTittel = "Nav trenger inntektsmelding",
                varslingInnhold = varslingInnhold,
                tidspunkt = null
            )
        }
    }
}
