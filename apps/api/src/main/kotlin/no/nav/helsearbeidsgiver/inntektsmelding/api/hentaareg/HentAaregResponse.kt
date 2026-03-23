package no.nav.helsearbeidsgiver.inntektsmelding.api.hentaareg

import kotlinx.serialization.Serializable
import no.nav.hag.simba.utils.felles.domene.PeriodeAapen

@Serializable
data class HentAaregResponse(
    val ansettelsesperioder: List<PeriodeAapen>,
)
