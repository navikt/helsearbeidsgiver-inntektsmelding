package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

import io.prometheus.client.Counter
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

/** Tar imot notifikasjon om at det er kommet en forespørsel om arbeidsgiveropplysninger. */
class ForespoerselMottattLoeser(
    rapid: RapidsConnection,
    private val priProducer: PriProducer,
) : River.PacketListener {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()
    private val forespoerselMottattCounter =
        Counter
            .build()
            .name("simba_forespoersel_mottatt_total")
            .help("Antall foresporsler mottatt fra Helsebro")
            .register()

    init {
        River(rapid)
            .apply {
                validate {
                    it.demandValues(
                        Pri.Key.NOTIS to Pri.NotisType.FORESPØRSEL_MOTTATT.name,
                    )
                    it.requireKeys(
                        Pri.Key.ORGNR,
                        Pri.Key.FNR,
                        Pri.Key.FORESPOERSEL_ID,
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

        val transaksjonId = randomUuid()

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.priNotis(Pri.NotisType.FORESPØRSEL_MOTTATT),
            Log.transaksjonId(transaksjonId),
        ) {
            runCatching {
                json.opprettEvent(transaksjonId, context)
            }.onFailure { e ->
                "Ukjent feil.".also {
                    logger.error("$it Se sikker logg for mer info.")
                    sikkerLogger.error(it, e)
                }

                json.republiser()
            }
        }
    }

    private fun JsonElement.opprettEvent(
        transaksjonId: UUID,
        context: MessageContext,
    ) {
        val melding = fromJsonMapFiltered(Pri.Key.serializer())

        logger.info("Mottok melding på pri-topic om ${Pri.NotisType.FORESPØRSEL_MOTTATT}.")
        sikkerLogger.info("Mottok melding på pri-topic:\n${toPretty()}")

        val orgnr = Pri.Key.ORGNR.les(String.serializer(), melding)
        val fnr = Pri.Key.FNR.les(String.serializer(), melding)
        val forespoerselId = Pri.Key.FORESPOERSEL_ID.les(UuidSerializer, melding)

        MdcUtils.withLogFields(
            Log.event(EventName.FORESPØRSEL_MOTTATT),
            Log.behov(BehovType.LAGRE_FORESPOERSEL),
            Log.forespoerselId(forespoerselId),
        ) {
            context
                .publish(
                    Key.EVENT_NAME to EventName.FORESPØRSEL_MOTTATT.toJson(EventName.serializer()),
                    Key.BEHOV to BehovType.LAGRE_FORESPOERSEL.toJson(BehovType.serializer()),
                    Key.ORGNRUNDERENHET to orgnr.toJson(),
                    Key.IDENTITETSNUMMER to fnr.toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                ).also {
                    logger.info("Publiserte melding. Se sikkerlogg for mer info.")
                    sikkerLogger.info("Publiserte melding:\n${it.toPretty()}")
                    forespoerselMottattCounter.inc()
                }
        }
    }

    private fun JsonElement.republiser() {
        priProducer.send(this)
    }
}
