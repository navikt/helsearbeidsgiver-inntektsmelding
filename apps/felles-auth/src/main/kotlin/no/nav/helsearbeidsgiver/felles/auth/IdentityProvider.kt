package no.nav.helsearbeidsgiver.felles.auth

enum class IdentityProvider(
    val verdi: String,
) {
    AZURE_AD("azuread"),
    IDPORTEN("idporten"),
    MASKINPORTEN("maskinporten"),
    TOKEN_X("tokenx"),
}
