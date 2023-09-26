package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import java.util.UUID

class OppgaveFerdigLoeser(
    rapid: RapidsConnection,
    private val agNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient
) : River.PacketListener {

    private val logger = logger()

    init {
        River(rapid).apply {
            validate {
                it.demandValues(
                    Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.name
                )
                it.requireKeys(
                    DataFelt.OPPGAVE_ID,
                    Key.FORESPOERSEL_ID,
                    Key.TRANSACTION_ORIGIN
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val json = packet.toJson().parseJson()

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(EventName.FORESPOERSEL_BESVART)
        ) {
            runCatching {
                json.haandterMelding(context)
            }
                .onFailure { e ->
                    "Ukjent feil. Republiserer melding.".also {
                        logger.error("$it Se sikker logg for mer info.")
                        sikkerLogger.error(it, e)

                        json.republiser(context)
                    }
                }
        }
    }

    private fun JsonElement.haandterMelding(context: MessageContext) {
        logger.info("Mottok melding med event '${EventName.FORESPOERSEL_BESVART}'.")
        sikkerLogger.info("Mottok melding:\n${toPretty()}")

        val melding = fromJsonMapFiltered(Key.serializer()) + fromJsonMapFiltered(DataFelt.serializer())

        val oppgaveId = DataFelt.OPPGAVE_ID.les(String.serializer(), melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)
        val transaksjonId = Key.TRANSACTION_ORIGIN.les(UuidSerializer, melding)

        MdcUtils.withLogFields(
            Log.oppgaveId(oppgaveId),
            Log.forespoerselId(forespoerselId),
            Log.transaksjonId(transaksjonId)
        ) {
            ferdigstillOppgave(oppgaveId, forespoerselId, transaksjonId, context)
        }
    }

    private fun ferdigstillOppgave(oppgaveId: String, forespoerselId: UUID, transaksjonId: UUID, context: MessageContext) {
        logger.info("Ferdigstiller oppgave...")
        val requestTimer = Metrics.requestLatency.labels("ferdigstillOppgave").startTimer()
        runBlocking {
            agNotifikasjonKlient.oppgaveUtfoert(oppgaveId)
        }.also {
            requestTimer.observeDuration()
        }
        context.publish(
            Key.EVENT_NAME to EventName.OPPGAVE_FERDIGSTILT.toJson(),
            DataFelt.OPPGAVE_ID to oppgaveId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Key.TRANSACTION_ORIGIN to transaksjonId.toJson()
        )

        logger.info("Oppgave ferdigstilt.")
    }

    private fun JsonElement.republiser(context: MessageContext) {
        context.publish(toString())
    }
}
