@file:UseSerializers(LocalDateSerializer::class, UuidSerializer::class)

package no.nav.hag.simba.kontrakt.domene.soeknad

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.time.LocalDate
import java.util.UUID

@Serializable
sealed class Soeknad {
    abstract val soeknadId: UUID
    abstract val sykmeldingId: UUID
    abstract val fom: LocalDate
    abstract val tom: LocalDate

    @Serializable
    data class Arbeidstaker(
        override val soeknadId: UUID,
        val vedtaksperiodeId: UUID,
        override val sykmeldingId: UUID,
        override val fom: LocalDate,
        override val tom: LocalDate,
        val erGradert: Boolean,
        val egenmeldingerFraSykmelding: Set<LocalDate>,
    ) : Soeknad()

    @Serializable
    data class Behandlingsdager(
        override val soeknadId: UUID,
        override val sykmeldingId: UUID,
        override val fom: LocalDate,
        override val tom: LocalDate,
        val behandlingsdager: Set<LocalDate>,
    ) : Soeknad()
}
