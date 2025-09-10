package no.nav.hag.simba.utils.auth

enum class IdentityProvider(
    val verdi: String,
) {
    AZURE_AD("azuread"),
    IDPORTEN("idporten"),
    MASKINPORTEN("maskinporten"),
    TOKEN_X("tokenx"),
}
