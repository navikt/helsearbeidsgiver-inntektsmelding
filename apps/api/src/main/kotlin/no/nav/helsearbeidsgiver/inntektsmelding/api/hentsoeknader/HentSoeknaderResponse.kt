@file:UseSerializers(LocalDateSerializer::class, UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.hentsoeknader

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.hag.simba.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.kontrakt.domene.soeknad.Soeknad
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.time.LocalDate
import java.util.UUID

@Serializable
data class HentSoeknaderResponse(
    val forespoersler: List<ForespoerselResponse>,
    val soeknaderArbeidstaker: List<SoeknadArbeidstakerResponse>,
    val soeknaderBehandlingsdager: List<SoeknadBehandlingsdagerResponse>,
)

@Serializable
data class ForespoerselResponse(
    val forespoerselId: UUID,
    val sykmeldingsperioder: List<Periode>,
    val egenmeldingsperioder: List<Periode>,
    val erBesvart: Boolean,
) {
    constructor(forespoerselMedId: Pair<UUID, Forespoersel>) : this(
        forespoerselId = forespoerselMedId.first,
        sykmeldingsperioder = forespoerselMedId.second.sykmeldingsperioder,
        egenmeldingsperioder = forespoerselMedId.second.egenmeldingsperioder,
        erBesvart = forespoerselMedId.second.erBesvart,
    )
}

@Serializable
data class SoeknadArbeidstakerResponse(
    val sykmeldingsperiode: Periode,
    val egenmeldingsperioder: List<Periode>,
    val erGradert: Boolean,
) {
    constructor(soeknad: Soeknad.Arbeidstaker) : this(
        sykmeldingsperiode = soeknad.sykmeldingsperiode,
        egenmeldingsperioder = soeknad.egenmeldingerFraSykmelding,
        erGradert = soeknad.erGradert,
    )
}

@Serializable
data class SoeknadBehandlingsdagerResponse(
    val sykmeldingsperiode: Periode,
    val behandlingsdager: List<LocalDate>,
) {
    constructor(soeknad: Soeknad.Behandlingsdager) : this(
        sykmeldingsperiode = soeknad.sykmeldingsperiode,
        behandlingsdager = soeknad.behandlingsdager.sorted(),
    )
}
