package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.metrics.recordTime
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class OppgaveFerdigLoeser(
    rapid: RapidsConnection,
    private val agNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
) : River.PacketListener {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    init {
        River(rapid)
            .apply {
                validate {
                    it.demandValues(
                        Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.name,
                    )
                    it.requireKeys(
                        Key.UUID,
                        Key.FORESPOERSEL_ID,
                        Key.OPPGAVE_ID,
                    )
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        if (packet[Key.FORESPOERSEL_ID.str].asText().isEmpty()) {
            logger.warn("Mangler forespørselId!")
            sikkerLogger.warn("Mangler forespørselId!")
        }
        val json = packet.toJson().parseJson()

        logger.info("Mottok melding med event '${EventName.FORESPOERSEL_BESVART}'.")
        sikkerLogger.info("Mottok melding:\n${json.toPretty()}")

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(EventName.FORESPOERSEL_BESVART),
        ) {
            runCatching {
                haandterMelding(json.toMap(), context)
            }.onFailure { e ->
                "Ukjent feil.".also {
                    logger.error("$it Se sikker logg for mer info.")
                    sikkerLogger.error(it, e)
                }
            }
        }
    }

    private fun haandterMelding(
        melding: Map<Key, JsonElement>,
        context: MessageContext,
    ) {
        val oppgaveId = Key.OPPGAVE_ID.les(String.serializer(), melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)

        MdcUtils.withLogFields(
            Log.oppgaveId(oppgaveId),
            Log.forespoerselId(forespoerselId),
            Log.transaksjonId(transaksjonId),
        ) {
            ferdigstillOppgave(oppgaveId, forespoerselId, transaksjonId, context)
        }
    }

    private fun ferdigstillOppgave(
        oppgaveId: String,
        forespoerselId: UUID,
        transaksjonId: UUID,
        context: MessageContext,
    ) {
        Metrics.agNotifikasjonRequest.recordTime(agNotifikasjonKlient::oppgaveUtfoert) {
            agNotifikasjonKlient.oppgaveUtfoert(oppgaveId)
        }

        context.publish(
            Key.EVENT_NAME to EventName.OPPGAVE_FERDIGSTILT.toJson(),
            Key.OPPGAVE_ID to oppgaveId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Key.UUID to transaksjonId.toJson(),
        )
    }
}
