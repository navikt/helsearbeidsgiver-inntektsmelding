@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.LocalDateSerializer
import java.time.LocalDate

@Serializable
data class Naturalytelse(
    val naturalytelseKode: NaturalytelseKode,
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
    aksjerGrunnfondsbevisTilUnderkurs,
    losji,
    kostDoegn,
    besoeksreiserHjemmetAnnet,
    kostbesparelseIHjemmet,
    rentefordelLaan,
    bil,
    kostDager,
    bolig,
    skattepliktigDelForsikringer,
    friTransport,
    opsjoner,
    tilskuddBarnehageplass,
    annet,
    bedriftsbarnehageplass,
    yrkebilTjenestligbehovKilometer,
    yrkebilTjenestligbehovListepris,
    innbetalingTilUtenlandskPensjonsordning,
    elektroniskKommunikasjon
}
