package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod
import no.nav.helsearbeidsgiver.tokenprovider.DefaultOAuth2HttpClient
import no.nav.helsearbeidsgiver.tokenprovider.OAuth2TokenProvider
import no.nav.helsearbeidsgiver.tokenprovider.TokenResolver
import no.nav.security.token.support.client.core.ClientAuthenticationProperties
import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.OAuth2GrantType
import no.nav.security.token.support.client.core.oauth2.ClientCredentialsTokenClient
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.core.oauth2.OnBehalfOfTokenClient
import no.nav.security.token.support.client.core.oauth2.TokenExchangeClient
import java.net.URI

fun OAuth2ClientConfig(environment: Environment): OAuth2TokenProvider {
    val oauth2HttpClient = DefaultOAuth2HttpClient()
    val oauth2Service = OAuth2AccessTokenService(
        TokenResolver(),
        OnBehalfOfTokenClient(oauth2HttpClient),
        ClientCredentialsTokenClient(oauth2HttpClient),
        TokenExchangeClient(oauth2HttpClient)
    )
    val clientPropertiesConfig = with(environment) {
        ClientProperties(
            URI(azureTokenEndpointUrl),
            URI(azureWellKnownUrl),
            OAuth2GrantType("client_credentials"),
            scope.split(","),
            ClientAuthenticationProperties(
                azureAppClientID,
                ClientAuthenticationMethod("client_secret_post"),
                azureAppClientSecret,
                azureAppJwk
            ),
            null,
            null
        )
    }
    return OAuth2TokenProvider(
        oauth2Service,
        clientPropertiesConfig
    )
}
