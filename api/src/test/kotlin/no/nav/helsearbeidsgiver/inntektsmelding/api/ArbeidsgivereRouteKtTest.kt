package no.nav.helsearbeidsgiver.inntektsmelding.api

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.helsearbeidsgiver.altinn.AltinnOrganisasjon
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.loeser.Løsning
import no.nav.helsearbeidsgiver.felles.loeser.LøsningFailure
import no.nav.helsearbeidsgiver.felles.loeser.LøsningSuccess
import no.nav.helsearbeidsgiver.inntektsmelding.api.arbeidsgivere.ArbeidsgivereRoute
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

private val objectMapper = customObjectMapper()

class ArbeidsgivereRouteKtTest {

    private val poller = mockk<RedisPoller>()

    @Test
    fun `gyldig request gir 200 OK med arbeidsgivere`() = testApplication {
        coEvery {
            poller.hent(any(), any(), any())
        } returns MockOk.response

        val response = routeTester().get()

        Assertions.assertEquals(HttpStatusCode.OK, response.status)

        val responseBody = response.body<Set<AltinnOrganisasjon>>()
        Assertions.assertEquals(responseBody, MockOk.responseBody)
    }

    @Test
    fun `feil fra løser gir 500 Internal Server Error med feilmelding`() = testApplication {
        coEvery {
            poller.hent(any(), any(), any())
        } returns MockInternalServerError.response

        val response = routeTester().get()

        Assertions.assertEquals(HttpStatusCode.InternalServerError, response.status)

        val responseBody = response.body<String>()
        Assertions.assertEquals(responseBody, MockInternalServerError.responseBody)
    }

    private fun ApplicationTestBuilder.routeTester(): RouteTester =
        RouteTester(
            this,
            poller,
            "/arbeidsgivere",
            RouteExtra::ArbeidsgivereRoute
        )
}

private object MockOk {
    val responseBody = setOf(
        AltinnOrganisasjon(
            name = "1_mockName",
            type = "1_mockType",
            parentOrganizationNumber = "1_mockParentOrganizationNumber",
            organizationForm = "1_mockOrganizationForm",
            organizationNumber = "1_mockOrganizationNumber",
            socialSecurityNumber = "1_mockSocialSecurityNumber",
            status = "1_mockStatus"
        ),
        AltinnOrganisasjon(
            name = "2_mockName",
            type = "2_mockType",
            parentOrganizationNumber = "2_mockParentOrganizationNumber",
            organizationForm = "2_mockOrganizationForm",
            organizationNumber = "2_mockOrganizationNumber",
            socialSecurityNumber = "2_mockSocialSecurityNumber",
            status = "2_mockStatus"
        )
    )

    val response = LøsningSuccess(responseBody)
        .toBehovMap()
        .toJsonNode()
}

private object MockInternalServerError {
    const val responseBody = "uff da!"

    val response = LøsningFailure(responseBody)
        .toBehovMap()
        .toJsonNode()
}

private fun Løsning<*>.toBehovMap(): Map<BehovType, Løsning<*>> =
    mapOf(
        BehovType.ARBEIDSGIVERE to this
    )

private fun Map<*, *>.toJsonNode(): JsonNode =
    objectMapper.valueToTree(this)
