@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val fnrFoedselsdatoFormatter = DateTimeFormatter.ofPattern("ddMMyyyy")

@Serializable
data class Person(
    val fnr: Fnr,
    val navn: String,
    val foedselsdato: LocalDate
) {
    companion object {
        // For info om fødselsdato i fnr, se https://lovdata.no/dokument/SF/forskrift/2017-07-14-1201/KAPITTEL_2#%C2%A72-2-1
        fun foedselsdato(fnr: Fnr): LocalDate {
            val foedtEtterAar39 = fnr.verdi[4].digitToInt() >= 4
            val individsifferUnder500 = fnr.verdi[6].digitToInt() < 5

            val aarhundre = if (foedtEtterAar39 || individsifferUnder500) {
                "19"
            } else {
                "20"
            }

            val foedseldato = fnr.verdi.take(4) + aarhundre + fnr.verdi.slice(4..5)

            return LocalDate.parse(foedseldato, fnrFoedselsdatoFormatter)
        }
    }
}
