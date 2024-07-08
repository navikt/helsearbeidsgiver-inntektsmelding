package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

class TelefonnummerValidator(input: String?) {
    init {
        require(input != null)
        require(isEightDigits(input) || startsWithPlus(input) || isTwelveDigits(input)) { "Ikke et gyldig telefonnummer" }
    }

    private fun startsWithPlus(input: String): Boolean {
        return """\+\d{10}""".toRegex().matches(input)
    }

    private fun isEightDigits(input: String) = """\d{8}""".toRegex().matches(input)

    private fun isTwelveDigits(input: String) = """00\d{10}""".toRegex().matches(input)

    companion object {
        fun isValid(telefonnummer: String?): Boolean {
            return try {
                TelefonnummerValidator(telefonnummer)
                true
            } catch (t: Throwable) {
                false
            }
        }
    }
}
