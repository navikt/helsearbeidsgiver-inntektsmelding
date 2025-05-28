package no.nav.helsearbeidsgiver.inntektsmelding.api.aktiveorgnr

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class AktiveOrgnrRequest(
    @JsonNames("sykmeldtFnr")
    val identitetsnummer: Fnr,
)
