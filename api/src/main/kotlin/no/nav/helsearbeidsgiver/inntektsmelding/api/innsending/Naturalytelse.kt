@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import java.time.LocalDate

@Serializable
data class Naturalytelse(
    val naturalytelse: NaturalytelseKode,
    val dato: LocalDate,
    val beløp: Double
)

/**
 * https://github.com/navikt/tjenestespesifikasjoner/blob/IM_nye_kodeverdier/nav-altinn-inntektsmelding/src/main/xsd/Inntektsmelding_kodelister_20210216.xsd
 */
/* ktlint-disable enum-entry-name-case */
/* ktlint-disable EnumEntryName */
@Suppress("EnumEntryName", "unused")
enum class NaturalytelseKode {
    AksjerGrunnfondsbevisTilUnderkurs,
    Losji,
    KostDoegn,
    BesoeksreiserHjemmetAnnet,
    KostbesparelseIHjemmet,
    RentefordelLaan,
    Bil,
    KostDager,
    Bolig,
    SkattepliktigDelForsikringer,
    FriTransport,
    Opsjoner,
    TilskuddBarnehageplass,
    Annet,
    Bedriftsbarnehageplass,
    YrkebilTjenestligbehovKilometer,
    YrkebilTjenestligbehovListepris,
    InnbetalingTilUtenlandskPensjonsordning,
    ElektroniskKommunikasjon
}
