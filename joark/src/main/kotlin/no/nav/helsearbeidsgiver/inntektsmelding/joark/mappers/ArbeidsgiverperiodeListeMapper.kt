package no.nav.helsearbeidsgiver.inntektsmelding.joark.mappers

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode
import org.mapstruct.Mapper

@Mapper
abstract class ArbeidsgiverperiodeListeMapper {

    fun mapArbeidsgiverperiodeListe(perioder: List<Periode>): List<no.seres.xsd.nav.inntektsmelding_m._20181211.Periode>? {
        if (perioder.isEmpty()) {
            return null
        } else {
            return perioder.map({ p -> mapPeriode(p) })
        }
    }

    abstract fun mapPeriode(periode: Periode): no.seres.xsd.nav.inntektsmelding_m._20181211.Periode
}
