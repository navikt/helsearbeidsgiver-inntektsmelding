package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
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

class OppgaveUtgaattLoeser(
    rapid: RapidsConnection,
    private val agNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val linkUrl: String,
) : River.PacketListener {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    init {
        River(rapid)
            .apply {
                validate {
                    it.demandValues(
                        Key.EVENT_NAME to EventName.FORESPOERSEL_FORKASTET.name,
                    )
                    it.requireKeys(
                        Key.UUID,
                        Key.FORESPOERSEL_ID,
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

        logger.info("Mottok melding med event '${EventName.FORESPOERSEL_FORKASTET}'.")
        sikkerLogger.info("Mottok melding:\n${json.toPretty()}")

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(EventName.FORESPOERSEL_FORKASTET),
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
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)

        MdcUtils.withLogFields(
            Log.forespoerselId(forespoerselId),
            Log.transaksjonId(transaksjonId),
        ) {
            settOppgaveUtgaatt(forespoerselId, transaksjonId, context)
        }
    }

    private fun settOppgaveUtgaatt(
        forespoerselId: UUID,
        transaksjonId: UUID,
        context: MessageContext,
    ) {
        Metrics.agNotifikasjonRequest.recordTime(agNotifikasjonKlient::oppgaveUtgaattByEksternId) {
            agNotifikasjonKlient.oppgaveUtgaattByEksternId(
                merkelapp = "Inntektsmelding sykepenger",
                eksternId = forespoerselId.toString(),
                nyLenke = "$linkUrl/im-dialog/utgatt",
            )
        }

        Metrics.agNotifikasjonRequest.recordTime(agNotifikasjonKlient::nyStatusSakByGrupperingsid) {
            agNotifikasjonKlient.nyStatusSakByGrupperingsid(
                grupperingsid = forespoerselId.toString(),
                merkelapp = "Inntektsmelding sykepenger",
                nyStatus = SaksStatus.FERDIG,
                tidspunkt = null,
            )
        }

        context.publish(
            Key.EVENT_NAME to EventName.OPPGAVE_UTGAATT.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Key.UUID to transaksjonId.toJson(),
        )
    }
}
