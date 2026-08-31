@file:UseSerializers(LocalDateSerializer::class, UuidSerializer::class)

package no.nav.hag.simba.kontrakt.domene.soeknad

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.time.LocalDate
import java.util.UUID

@Serializable
sealed class Soeknad {
    abstract val soeknadId: UUID
    abstract val sykmeldingsperiode: Periode

    @Serializable
    data class Arbeidstaker(
        override val soeknadId: UUID,
        val vedtaksperiodeId: UUID,
        override val sykmeldingsperiode: Periode,
        val egenmeldingerFraSykmelding: List<Periode>,
        val erGradert: Boolean,
    ) : Soeknad()

    @Serializable
    data class Behandlingsdager(
        override val soeknadId: UUID,
        override val sykmeldingsperiode: Periode,
        val behandlingsdager: Set<LocalDate>,
    ) : Soeknad()
}
