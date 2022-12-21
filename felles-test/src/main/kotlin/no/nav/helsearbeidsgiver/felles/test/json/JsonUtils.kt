package no.nav.helsearbeidsgiver.felles.test.json

private val jsonWhitespaceRegex = Regex("""("(?:\\"|[^"])*")|\s""")

fun String.removeJsonWhitespace(): String =
    replace(jsonWhitespaceRegex, "$1")
