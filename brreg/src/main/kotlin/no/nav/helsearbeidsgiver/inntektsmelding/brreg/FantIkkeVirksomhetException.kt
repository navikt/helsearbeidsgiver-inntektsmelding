package no.nav.helsearbeidsgiver.inntektsmelding.brreg

class FantIkkeVirksomhetException(
    orgnr: String,
) : Exception("Fant ikke virksomhet for orgnr $orgnr")
