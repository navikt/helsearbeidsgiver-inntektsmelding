package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import io.prometheus.client.Summary
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.publishData
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import java.util.UUID

class TilgangLoeser(
    rapidsConnection: RapidsConnection,
    private val altinnClient: AltinnClient
) : Loeser(rapidsConnection) {

    private val requestLatency = Summary.build()
        .name("simba_altinn_tilgangskontroll_latency_seconds")
        .help("altinn tilgangskontroll latency in seconds")
        .register()

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValues(Key.BEHOV to BehovType.TILGANGSKONTROLL.name)
            it.interestedIn(
                Key.UUID,
                Key.ORGNRUNDERENHET,
                Key.FNR,
                Key.FORESPOERSEL_ID
            )
        }
    }

    override fun onBehov(behov: Behov) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(behov.event),
            Log.behov(BehovType.TILGANGSKONTROLL)
        ) {
            val json = behov.jsonMessage.toJson().parseJson().toMap()

            val transaksjonId = Key.UUID.les(UuidSerializer, json)
            MdcUtils.withLogFields(
                Log.transaksjonId(transaksjonId)
            ) {
                runCatching {
                    hentTilgang(behov, json, transaksjonId)
                }
                    .onFailure {
                        behov.createFail("Ukjent feil.")
                            .also(this::publishFail)
                    }
            }
        }
    }

    private fun hentTilgang(behov: Behov, json: Map<IKey, JsonElement>, transaksjonId: UUID) {
        val fnr = Key.FNR.les(String.serializer(), json)
        val orgnr = Key.ORGNRUNDERENHET.les(String.serializer(), json)

        val requestTimer = requestLatency.startTimer()
        runCatching {
            runBlocking {
                altinnClient.harRettighetForOrganisasjon(fnr, orgnr)
            }
        }.also {
            requestTimer.observeDuration()
        }
            .onSuccess { harTilgang ->
                val tilgang = if (harTilgang) Tilgang.HAR_TILGANG else Tilgang.IKKE_TILGANG

                rapidsConnection.publishData(
                    eventName = behov.event,
                    transaksjonId = transaksjonId,
                    forespoerselId = behov.forespoerselId?.let(UUID::fromString),
                    Key.TILGANG to tilgang.toJson(Tilgang.serializer())
                )
            }
            .onFailure {
                behov.createFail("Feil ved henting av rettigheter fra Altinn.")
                    .also(this::publishFail)
            }
    }
}
