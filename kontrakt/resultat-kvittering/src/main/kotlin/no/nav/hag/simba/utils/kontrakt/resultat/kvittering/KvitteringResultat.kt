package no.nav.hag.simba.utils.kontrakt.resultat.kvittering

import kotlinx.serialization.Serializable
import no.nav.hag.simba.utils.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.utils.kontrakt.domene.inntektsmelding.LagretInntektsmelding

@Serializable
data class KvitteringResultat(
    val forespoersel: Forespoersel,
    val sykmeldtNavn: String,
    val orgNavn: String,
    val lagret: LagretInntektsmelding?,
)
