package no.nav.helsearbeidsgiver.inntektsmelding.api.mock

import no.nav.helsearbeidsgiver.altinn.AltinnOrganisasjon

fun mockOrganisasjoner(): List<AltinnOrganisasjon> {
    return listOf(
        org("Norge AS", "123456789"),
        org("Sverige AS", "123456789"),
        org("Danmark AS", "123456789"),
    )
}

fun org(navn: String, orgnr: String): AltinnOrganisasjon {
    return AltinnOrganisasjon(navn, "Business","910020102","AS",orgnr,"", "ACTIVE")
}
