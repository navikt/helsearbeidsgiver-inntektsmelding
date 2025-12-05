package no.nav.hag.simba.kontrakt.resultat.kvittering

import kotlinx.serialization.Serializable
import no.nav.hag.simba.kontrakt.domene.inntektsmelding.LagretInntektsmelding
import no.nav.helsearbeidsgiver.domene.forespoersel.Forespoersel

@Serializable
data class KvitteringResultat(
    val forespoersel: Forespoersel,
    val sykmeldtNavn: String,
    val orgNavn: String,
    val lagret: LagretInntektsmelding?,
)
