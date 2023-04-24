package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock

import no.nav.helsearbeidsgiver.pdl.PdlHentFullPerson
import no.nav.helsearbeidsgiver.pdl.PdlPersonNavnMetadata
import java.time.LocalDate

fun mockPerson(fornavn: String, mellomNavn: String, etternavn: String, fødselsdato: LocalDate): PdlHentFullPerson {
    return PdlHentFullPerson(
        hentPerson = PdlHentFullPerson.PdlFullPersonliste(
            navn = listOf(PdlHentFullPerson.PdlFullPersonliste.PdlNavn(fornavn, mellomNavn, etternavn, PdlPersonNavnMetadata(""))),
            foedsel = listOf(PdlHentFullPerson.PdlFullPersonliste.PdlFoedsel(fødselsdato)),
            doedsfall = emptyList(),
            adressebeskyttelse = emptyList(),
            statsborgerskap = emptyList(),
            bostedsadresse = emptyList(),
            kjoenn = emptyList()
        ),
        hentIdenter = PdlHentFullPerson.PdlIdentResponse(
            emptyList()
        ),
        hentGeografiskTilknytning = PdlHentFullPerson.PdlGeografiskTilknytning(
            PdlHentFullPerson.PdlGeografiskTilknytning.PdlGtType.KOMMUNE,
            null,
            null,
            null
        )
    )
}
