@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.soeknad.klient

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import java.time.LocalDate

@Serializable
internal data class HentSoeknaderRequest(
    val orgnummer: String,
    val fnr: String,
    val eldsteFom: LocalDate,
)

@Serializable
internal data class HentSoeknaderResponse(
    val sykepengesoknadUuid: String,
    val vedtaksperiodeId: String?,
    val sykmeldingId: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val soknadstype: Soeknadstype,
    val soknadsperioder: List<Sykmeldingsgrad>,
    val egenmeldingsdagerFraSykmelding: List<LocalDate>,
    val behandlingsdager: List<LocalDate>,
) {
    @Serializable
    enum class Soeknadstype {
        ARBEIDSTAKERE,
        BEHANDLINGSDAGER,
    }

    @Serializable
    data class Sykmeldingsgrad(
        val grad: Int,
        val faktiskGrad: Int?,
    )
}
