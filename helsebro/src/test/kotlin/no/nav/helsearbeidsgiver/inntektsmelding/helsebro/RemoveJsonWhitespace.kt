package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

private val jsonWhitespaceRegex = Regex("""("(?:\\"|[^"])*")|\s""")

fun String.removeJsonWhitespace(): String =
    replace(jsonWhitespaceRegex, "$1")
