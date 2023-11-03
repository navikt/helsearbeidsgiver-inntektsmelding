package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart

import io.prometheus.client.Counter
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer

class ForespoerselBesvartFraSpleisLoeser(
    rapid: RapidsConnection,
    private val priProducer: PriProducer
) : ForespoerselBesvartLoeser() {

    override val forespoerselBesvartCounter: Counter = Counter.build()
        .name("simba_forespoersel_besvart_fra_spleis_total")
        .help("Antall foresporsler besvart fra Spleis (pri-topic)")
        .register()

    init {
        River(rapid).apply {
            validate {
                it.demandValues(
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.name
                )
                it.requireKeys(
                    Pri.Key.FORESPOERSEL_ID
                )
                it.interestedIn(
                    Pri.Key.SPINN_INNTEKTSMELDING_ID
                )
            }
        }.register(this)
    }

    override fun Map<IKey, JsonElement>.lesMelding(): Melding =
        Melding(
            event = Pri.NotisType.FORESPOERSEL_BESVART.name,
            forespoerselId = Pri.Key.FORESPOERSEL_ID.les(UuidSerializer, this),
            transaksjonId = randomUuid(),
            spinnInntektsmeldingId = Pri.Key.SPINN_INNTEKTSMELDING_ID.lesOrNull(UuidSerializer, this)
        )

    override fun haandterFeil(json: JsonElement) {
        priProducer.send(json)
    }
}
