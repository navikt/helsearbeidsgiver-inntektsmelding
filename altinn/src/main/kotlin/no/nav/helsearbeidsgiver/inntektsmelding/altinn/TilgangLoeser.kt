package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.toUUID
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class TilgangLoeser(
    rapidsConnection: RapidsConnection,
    private val altinnClient: AltinnClient
) : Løser(rapidsConnection) {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValues(Key.BEHOV to BehovType.TILGANGSKONTROLL.name)
            it.interestedIn(
                Key.UUID,
                DataFelt.ORGNRUNDERENHET,
                DataFelt.FNR
            )
        }
    }

    override fun onBehov(packet: JsonMessage) {
    }

    override fun onBehov(behov: Behov) {
/*  @TODO implement logging in Løser
        logger.info("Mottok melding med behov '${BehovType.TILGANGSKONTROLL}'.")
        sikkerLogger.info("Mottok melding:\n${json.toPretty()}")
*/
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.behov(BehovType.TILGANGSKONTROLL)
        ) {
            behov.withLogFields {
                runCatching {
                    hentTilgang(it)
                }
                    .onFailure { feil ->
                        behov.createFail("Ukjent feil.")
                            .also(this::publishFail)
                    }
            }
        }
    }

    // @TODO log incoming and outgoing message

    private fun hentTilgang(behov: Behov) {
        runCatching {
            runBlocking {
                altinnClient.harRettighetForOrganisasjon(behov[DataFelt.FNR].asText(), behov[DataFelt.ORGNRUNDERENHET].asText())
            }
        }
            .onSuccess { harTilgang ->
                val tilgang = if (harTilgang) Tilgang.HAR_TILGANG else Tilgang.IKKE_TILGANG
                publishData(
                    behov.createData(
                        mapOf(
                            DataFelt.TILGANG to tilgang
                        )
                    )
                )
            }
            .onFailure {
                behov.createFail("Feil ved henting av rettigheter fra Altinn.")
                    .also(this::publishFail)
                    .toJsonMessage()
                    .toJson()
                    .also {
                        logger.error("Publiserte feil for ${BehovType.TILGANGSKONTROLL}.")
                        sikkerLogger.error("Publiserte feil:\n${it.parseJson().toPretty()}")
                    }
            }
    }
}

fun Behov.withLogFields(block: (Behov) -> Unit) {
    MdcUtils.withLogFields(
        Log.event(event),
        Log.transaksjonId(uuid().toUUID())
    ) {
        block(this)
    }
}
