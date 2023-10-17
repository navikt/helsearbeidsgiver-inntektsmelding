package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart

import io.prometheus.client.Counter
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.rejectKeys
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class ForespoerselBesvartFraSimbaLoeser(
    rapid: RapidsConnection
) : ForespoerselBesvartLoeser() {

    private val sikkerLogger = sikkerLogger()

    override val forespoerselBesvartCounter: Counter = Counter.build()
        .name("simba_forespoersel_besvart_fra_simba_total")
        .help("Antall foresporsler besvart fra Simba")
        .register()

    init {
        River(rapid).apply {
            validate {
                it.demandValues(
                    Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.name
                )
                it.requireKeys(
                    Key.FORESPOERSEL_ID,
                    Key.TRANSACTION_ORIGIN
                )
                it.rejectKeys(
                    Key.BEHOV
                )
            }
        }.register(this)
    }

    override fun Map<IKey, JsonElement>.lesMelding(): Melding =
        Melding(
            event = EventName.INNTEKTSMELDING_MOTTATT.name,
            forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, this),
            transaksjonId = Key.TRANSACTION_ORIGIN.les(UuidSerializer, this),
            spinnInntektsmeldingId = null
        )

    override fun haandterFeil(json: JsonElement) {
        sikkerLogger.error(
            "Klarte ikke umiddelbart markere forespørsel som besvart. " +
                "Event fra helsebro skal løse dette. Aktuell melding:\n${json.toPretty()}"
        )
    }
}
