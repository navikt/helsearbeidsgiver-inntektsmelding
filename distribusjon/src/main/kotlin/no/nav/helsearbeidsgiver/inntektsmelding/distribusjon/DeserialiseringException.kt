package no.nav.helsearbeidsgiver.inntektsmelding.distribusjon

class DeserialiseringException(
    exception: Exception
) : Exception("Klarte ikke deserialisere InntektsmeldingDokument!", exception)
