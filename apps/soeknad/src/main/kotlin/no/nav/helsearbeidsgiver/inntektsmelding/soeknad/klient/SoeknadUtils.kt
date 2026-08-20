package no.nav.helsearbeidsgiver.inntektsmelding.soeknad.klient

import no.nav.hag.simba.kontrakt.domene.soeknad.Soeknad
import java.util.UUID

internal fun tilSoeknad(response: HentSoeknaderResponse): Soeknad? {
    val soeknadId = response.sykepengesoknadUuid.let(UUID::fromString)
    val vedtaksperiodeId = response.vedtaksperiodeId?.let(UUID::fromString)
    val sykmeldingId = response.sykmeldingId.let(UUID::fromString)
    val fom = response.fom
    val tom = response.tom
    val erGradert =
        response.soknadsperioder.any {
            it.grad < 100 ||
                (it.faktiskGrad != null && it.faktiskGrad > 0)
        }
    val egenmeldingerFraSykmelding = response.egenmeldingsdagerFraSykmelding.toSet()
    val behandlingsdager = response.behandlingsdager.toSet()

    return when (response.soknadstype) {
        HentSoeknaderResponse.Soeknadstype.ARBEIDSTAKERE -> {
            if (vedtaksperiodeId == null) {
                null
            } else {
                Soeknad.Arbeidstaker(
                    soeknadId = soeknadId,
                    vedtaksperiodeId = vedtaksperiodeId,
                    sykmeldingId = sykmeldingId,
                    fom = fom,
                    tom = tom,
                    erGradert = erGradert,
                    egenmeldingerFraSykmelding = egenmeldingerFraSykmelding,
                )
            }
        }

        HentSoeknaderResponse.Soeknadstype.BEHANDLINGSDAGER -> {
            if (behandlingsdager.isEmpty()) {
                null
            } else {
                Soeknad.Behandlingsdager(
                    soeknadId = soeknadId,
                    sykmeldingId = sykmeldingId,
                    fom = fom,
                    tom = tom,
                    behandlingsdager = behandlingsdager,
                )
            }
        }
    }
}
