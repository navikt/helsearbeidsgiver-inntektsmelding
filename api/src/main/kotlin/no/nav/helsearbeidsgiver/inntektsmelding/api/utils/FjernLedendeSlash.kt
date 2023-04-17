package no.nav.helsearbeidsgiver.inntektsmelding.api.utils

// Midlertidig workaround, frontend leverer ?uuid=/<uuid> foreløpig
fun fjernLedendeSlash(str: String): String {
    if (str.isNotEmpty() and str.startsWith("/")) {
        return str.substring(1)
    }
    return str
}
