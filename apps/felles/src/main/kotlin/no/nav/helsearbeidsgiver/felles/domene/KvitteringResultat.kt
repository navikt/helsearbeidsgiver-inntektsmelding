package no.nav.helsearbeidsgiver.felles.domene

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding

@Serializable
data class KvitteringResultat(
    val forespoersel: Forespoersel,
    val sykmeldtNavn: String,
    val avsenderNavn: String,
    val orgNavn: String,
    val skjema: SkjemaInntektsmelding?,
    val inntektsmelding: Inntektsmelding?,
    val eksternInntektsmelding: EksternInntektsmelding?,
)
