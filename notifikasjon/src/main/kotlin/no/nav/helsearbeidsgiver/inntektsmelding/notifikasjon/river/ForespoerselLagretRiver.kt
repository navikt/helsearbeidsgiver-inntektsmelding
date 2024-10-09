package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.rejectKeys
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class ForespoerselLagretRiver(
    rapid: RapidsConnection,
) : River.PacketListener {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    init {
        River(rapid)
            .apply {
                validate {
                    it.demandValues(
                        Key.EVENT_NAME to EventName.FORESPØRSEL_LAGRET.name,
                    )
                    it.rejectKeys(
                        Key.BEHOV,
                        Key.DATA,
                        Key.FAIL,
                    )
                    it.requireKeys(
                        Key.FORESPOERSEL_ID,
                        Key.ORGNRUNDERENHET,
                        Key.IDENTITETSNUMMER,
                    )
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        "Mottok event ${EventName.FORESPØRSEL_LAGRET}. Sender event ${EventName.SAK_OPPRETT_REQUESTED} og ${EventName.OPPGAVE_OPPRETT_REQUESTED}.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        val melding = packet.toJson().parseJson().toMap()

        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)
        val orgnr = Key.ORGNRUNDERENHET.les(String.serializer(), melding)
        val fnr = Key.IDENTITETSNUMMER.les(String.serializer(), melding)

        context.publish(
            Key.EVENT_NAME to EventName.SAK_OPPRETT_REQUESTED.toJson(),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.ORGNRUNDERENHET to orgnr.toJson(),
                    Key.IDENTITETSNUMMER to fnr.toJson(),
                ).toJson(),
        )

        context.publish(
            Key.EVENT_NAME to EventName.OPPGAVE_OPPRETT_REQUESTED.toJson(),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.ORGNRUNDERENHET to orgnr.toJson(),
                ).toJson(),
        )
    }
}
