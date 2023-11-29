package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import io.prometheus.client.Summary
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.toUUID
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.log.MdcUtils

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
                Key.FNR
            )
        }
    }

    override fun onBehov(behov: Behov) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.behov(BehovType.TILGANGSKONTROLL)
        ) {
            behov.withLogFields {
                runCatching {
                    hentTilgang(it)
                }
                    .onFailure {
                        behov.createFail("Ukjent feil.")
                            .also(this::publishFail)
                    }
            }
        }
    }

    private fun hentTilgang(behov: Behov) {
        val requestTimer = requestLatency.startTimer()
        runCatching {
            runBlocking {
                altinnClient.harRettighetForOrganisasjon(behov.jsonMessage[Key.FNR.str].asText(), behov.jsonMessage[Key.ORGNRUNDERENHET.str].asText())
            }
        }.also {
            requestTimer.observeDuration()
        }
            .onSuccess { harTilgang ->
                val tilgang = if (harTilgang) Tilgang.HAR_TILGANG else Tilgang.IKKE_TILGANG
                publishData(
                    behov.createData(
                        mapOf(
                            Key.TILGANG to tilgang
                        )
                    )
                )
            }
            .onFailure {
                behov.createFail("Feil ved henting av rettigheter fra Altinn.")
                    .also(this::publishFail)
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
