package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import io.prometheus.client.Summary
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.altinn.AltinnOrganisasjon
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.loeser.Løser
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson

class AltinnLøser(
    private val altinnClient: AltinnClient
) : Løser<Set<AltinnOrganisasjon>>() {
    override val behovType = BehovType.ARBEIDSGIVERE

    lateinit var identitetsnummer: Behov.() -> String
    private val requestLatency = Summary.build().name("altinn_hent_rettighet_organisasjoner_latency_seconds").help(
        "altinn hentrettighetOrganisasjoner latency in seconds"
    ).register()

    init {
        start()
    }

    override fun BehovReader.createReaders() {
        identitetsnummer = readFn(Key.IDENTITETSNUMMER)
    }

    override fun Behov.løs(): Set<AltinnOrganisasjon> {
        val requestTimer = requestLatency.startTimer()
        runBlocking {
            altinnClient.hentRettighetOrganisasjoner(identitetsnummer())
        }.also {
            requestTimer.observeDuration()
            return it
        }
    }

    override fun Set<AltinnOrganisasjon>.toJson(): JsonElement =
        toJson(AltinnOrganisasjon.serializer().set())
}
