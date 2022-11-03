package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.altinn.AltinnOrganisasjon
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.loeser.Løser

class AltinnLøser(
    private val altinnClient: AltinnClient
) : Løser() {
    override val behovType = BehovType.ARBEIDSGIVERE

    lateinit var identitetsnummer: Behov.() -> String

    init {
        start()
    }

    override fun BehovReader.createReaders() {
        identitetsnummer = readFn(Key.IDENTITETSNUMMER)
    }

    override fun Behov.løs(): Set<AltinnOrganisasjon> =
        runBlocking {
            altinnClient.hentRettighetOrganisasjoner(identitetsnummer())
        }
}
