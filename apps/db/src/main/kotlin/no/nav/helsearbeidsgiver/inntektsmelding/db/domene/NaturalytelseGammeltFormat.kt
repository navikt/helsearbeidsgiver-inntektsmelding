@file:Suppress("DEPRECATION")
@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.db.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import java.time.LocalDate

@Deprecated("Kun brukt for å lese gamle databaserader.")
@Serializable
data class NaturalytelseGammeltFormat(
    val naturalytelse: NaturalytelseKodeGammeltFormat,
    val dato: LocalDate,
    val beløp: Double,
)

@Deprecated("Kun brukt for å lese gamle databaserader.")
@Serializable
enum class NaturalytelseKodeGammeltFormat {
    AKSJERGRUNNFONDSBEVISTILUNDERKURS,
    ANNET,
    BEDRIFTSBARNEHAGEPLASS,
    BESOEKSREISERHJEMMETANNET,
    BIL,
    BOLIG,
    ELEKTRONISKKOMMUNIKASJON,
    FRITRANSPORT,
    INNBETALINGTILUTENLANDSKPENSJONSORDNING,
    KOSTBESPARELSEIHJEMMET,
    KOSTDAGER,
    KOSTDOEGN,
    LOSJI,
    OPSJONER,
    RENTEFORDELLAAN,
    SKATTEPLIKTIGDELFORSIKRINGER,
    TILSKUDDBARNEHAGEPLASS,
    YRKEBILTJENESTLIGBEHOVKILOMETER,
    YRKEBILTJENESTLIGBEHOVLISTEPRIS,
}
