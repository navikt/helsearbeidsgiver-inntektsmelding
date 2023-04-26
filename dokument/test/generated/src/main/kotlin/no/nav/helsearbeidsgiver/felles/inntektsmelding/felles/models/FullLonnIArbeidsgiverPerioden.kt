package no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import javax.validation.constraints.NotNull
import kotlin.Boolean

data class FullLonnIArbeidsgiverPerioden(
    @param:JsonProperty("utbetalerFullLønn")
    @get:JsonProperty("utbetalerFullLønn")
    @get:NotNull
    val utbetalerFullLønn: Boolean,
    @param:JsonProperty("begrunnelse")
    @get:JsonProperty("begrunnelse")
    val begrunnelse: BegrunnelseIngenEllerRedusertUtbetalingKode? = null,
    @param:JsonProperty("utbetalt")
    @get:JsonProperty("utbetalt")
    val utbetalt: BigDecimal? = null
)
