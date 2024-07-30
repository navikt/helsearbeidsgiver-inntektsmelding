package no.nav.helsearbeidsgiver.inntektsmelding.joark.mappers

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import org.mapstruct.Mapper

@Mapper
abstract class ArbeidsgiverperiodeListeMapper {
    fun mapArbeidsgiverperiodeListe(perioder: List<Periode>): List<no.seres.xsd.nav.inntektsmelding_m._20181211.Periode>? =
        if (perioder.isEmpty()) {
            null
        } else {
            perioder.map(::mapPeriode)
        }

    abstract fun mapPeriode(periode: Periode): no.seres.xsd.nav.inntektsmelding_m._20181211.Periode
}
