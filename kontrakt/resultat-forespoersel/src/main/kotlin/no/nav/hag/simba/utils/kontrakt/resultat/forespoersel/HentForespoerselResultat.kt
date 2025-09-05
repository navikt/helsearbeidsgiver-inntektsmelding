@file:UseSerializers(YearMonthSerializer::class)

package no.nav.hag.simba.utils.kontrakt.resultat.forespoersel

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.hag.simba.utils.kontrakt.domene.forespoersel.Forespoersel
import no.nav.helsearbeidsgiver.utils.json.serializer.YearMonthSerializer
import java.time.YearMonth

@Serializable
data class HentForespoerselResultat(
    val sykmeldtNavn: String?,
    val avsenderNavn: String?,
    val orgNavn: String?,
    val inntekt: Map<YearMonth, Double?>?,
    val forespoersel: Forespoersel,
)
