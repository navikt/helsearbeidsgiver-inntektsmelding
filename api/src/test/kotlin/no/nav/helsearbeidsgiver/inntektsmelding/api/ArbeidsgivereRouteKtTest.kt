package no.nav.helsearbeidsgiver.inntektsmelding.api

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import no.nav.helsearbeidsgiver.altinn.AltinnOrganisasjon
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.loeser.toLøsningFailure
import no.nav.helsearbeidsgiver.felles.loeser.toLøsningSuccess
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

private val objectMapper = customObjectMapper()

private const val PATH = Routes.PREFIX + Routes.ARBEIDSGIVERE

class ArbeidsgivereRouteKtTest : ApiTest() {

    @Test
    fun `gyldig request gir 200 OK med arbeidsgivere`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().hent(any(), any(), any())
        } returns MockOk.response

        val response = get(PATH)

        Assertions.assertEquals(HttpStatusCode.OK, response.status)

        val responseBody = response.body<Set<AltinnOrganisasjon>>()
        Assertions.assertEquals(responseBody, MockOk.responseBody)
    }

    @Test
    fun `feil fra løser gir 500 Internal Server Error med feilmelding`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().hent(any(), any(), any())
        } returns MockInternalServerError.response

        val response = get(PATH)

        Assertions.assertEquals(HttpStatusCode.InternalServerError, response.status)

        val responseBody = response.body<String>()
        Assertions.assertEquals(responseBody, MockInternalServerError.responseBody)
    }
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

    val response = responseBody.toLøsningSuccess()
        .let(Json::encodeToJsonElement)
        .toJsonNode()
        .toBehovMap()
        .toJsonNode()
}

private object MockInternalServerError {
    const val responseBody = "uff da!"

    val response = responseBody.toLøsningFailure()
        .let(Json::encodeToJsonElement)
        .toJsonNode()
        .toBehovMap()
        .toJsonNode()
}

private fun JsonNode.toBehovMap(): Map<BehovType, JsonNode> =
    mapOf(
        BehovType.ARBEIDSGIVERE to this
    )

private fun JsonElement.toJsonNode(): JsonNode =
    toString().let(objectMapper::readTree)

private fun Map<*, *>.toJsonNode(): JsonNode =
    objectMapper.valueToTree(this)
