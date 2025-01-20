package no.nav.helsearbeidsgiver.felles.domene

import kotlinx.serialization.Serializable

@Serializable
data class KvitteringResultat(
    val forespoersel: Forespoersel,
    val sykmeldtNavn: String,
    val orgNavn: String,
    val lagret: LagretInntektsmelding?,
)
