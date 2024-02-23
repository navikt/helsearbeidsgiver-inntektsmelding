package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

// TODO slett etter overgangsfase
class DeserialiseringException(
    exception: Exception
) : Exception("Klarte ikke deserialisere Inntektsmelding!", exception)
