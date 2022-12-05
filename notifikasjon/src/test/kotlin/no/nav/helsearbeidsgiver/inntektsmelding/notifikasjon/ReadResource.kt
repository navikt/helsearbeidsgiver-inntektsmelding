package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

fun readResource(filename: String) = ClassLoader.getSystemResource(filename).readText()
