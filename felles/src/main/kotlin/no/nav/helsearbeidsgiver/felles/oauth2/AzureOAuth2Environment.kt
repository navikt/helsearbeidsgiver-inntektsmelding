package no.nav.helsearbeidsgiver.felles.oauth2

data class AzureOAuth2Environment(
    val scope: String,
    val azureWellKnownUrl: String,
    val azureTokenEndpointUrl: String,
    val azureAppClientID: String,
    val azureAppClientSecret: String,
    val azureAppJwk: String
)
