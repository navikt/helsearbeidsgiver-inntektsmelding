@file:UseSerializers(LocalDateSerializer::class, UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.hentsoeknader

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.hag.simba.kontrakt.domene.soeknad.Soeknad
import no.nav.hag.simba.kontrakt.resultat.soeknad.ForespoerselMedId
import no.nav.hag.simba.kontrakt.resultat.soeknad.SoeknadMedForlengerId
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
    val vedtaksperiodeId: UUID,
    val sykmeldingsperioder: List<Periode>,
    val egenmeldingsperioder: List<Periode>,
    val erBesvart: Boolean,
) {
    constructor(forespoerselMedId: ForespoerselMedId) : this(
        forespoerselId = forespoerselMedId.forespoerselId,
        vedtaksperiodeId = forespoerselMedId.forespoersel.vedtaksperiodeId,
        sykmeldingsperioder = forespoerselMedId.forespoersel.sykmeldingsperioder,
        egenmeldingsperioder = forespoerselMedId.forespoersel.egenmeldingsperioder,
        erBesvart = forespoerselMedId.forespoersel.erBesvart,
    )
}

@Serializable
data class SoeknadArbeidstakerResponse(
    val vedtaksperiodeId: UUID,
    val sykmeldingsperiode: Periode,
    val egenmeldingsperioder: List<Periode>,
    val erGradert: Boolean,
    val forlengerVedtaksperiodeId: UUID?,
) {
    constructor(soeknad: SoeknadMedForlengerId) : this(
        vedtaksperiodeId = soeknad.soeknad.vedtaksperiodeId,
        sykmeldingsperiode = soeknad.soeknad.sykmeldingsperiode,
        egenmeldingsperioder = soeknad.soeknad.egenmeldingerFraSykmelding,
        erGradert = soeknad.soeknad.erGradert,
        forlengerVedtaksperiodeId = soeknad.forlengerVedtaksperiodeId,
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
