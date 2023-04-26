package no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models

import com.fasterxml.jackson.annotation.JsonValue
import kotlin.String
import kotlin.collections.Map

enum class BegrunnelseIngenEllerRedusertUtbetalingKode(
    @JsonValue
    val value: String
) {
    LOVLIG_FRAVAER("LovligFravaer"),

    FRAVAER_UTEN_GYLDIG_GRUNN("FravaerUtenGyldigGrunn"),

    ARBEID_OPPHOERT("ArbeidOpphoert"),

    BESKJED_GITT_FOR_SENT("BeskjedGittForSent"),

    MANGLER_OPPTJENING("ManglerOpptjening"),

    IKKE_LOENN("IkkeLoenn"),

    BETVILER_ARBEIDSUFOERHET("BetvilerArbeidsufoerhet"),

    IKKE_FRAVAER("IkkeFravaer"),

    STREIK_ELLER_LOCKOUT("StreikEllerLockout"),

    PERMITTERING("Permittering"),

    FISKER_MED_HYRE("FiskerMedHyre"),

    SAERREGLER("Saerregler"),

    FERIE_ELLER_AVSPASERING("FerieEllerAvspasering"),

    IKKE_FULL_STILLINGSANDEL("IkkeFullStillingsandel"),

    TIDLIGERE_VIRKSOMHET("TidligereVirksomhet");

    companion object {
        private val mapping: Map<String, BegrunnelseIngenEllerRedusertUtbetalingKode> =
            values().associateBy(BegrunnelseIngenEllerRedusertUtbetalingKode::value)

        fun fromValue(value: String): BegrunnelseIngenEllerRedusertUtbetalingKode? = mapping[value]
    }
}
