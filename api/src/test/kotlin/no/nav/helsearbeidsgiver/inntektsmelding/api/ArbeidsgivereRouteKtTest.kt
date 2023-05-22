package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.NothingSerializer
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.altinn.AltinnOrganisasjon
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.json.løsning
import no.nav.helsearbeidsgiver.felles.loeser.toLøsningFailure
import no.nav.helsearbeidsgiver.felles.loeser.toLøsningSuccess
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.ApiTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

private const val PATH = Routes.PREFIX + Routes.ARBEIDSGIVERE

class ArbeidsgivereRouteKtTest : ApiTest() {

    @Test
    fun `gyldig request gir 200 OK med arbeidsgivere`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().hent(any(), any(), any())
        } returns MockOk.response

        val response = get(PATH)

        Assertions.assertEquals(HttpStatusCode.OK, response.status)

        val responseBody = response.bodyAsText().fromJson(AltinnOrganisasjon.serializer().set())
        Assertions.assertEquals(MockOk.responseBody, responseBody)
    }

    @Test
    fun `feil fra løser gir 500 Internal Server Error med feilmelding`() = testApi {
        coEvery {
            anyConstructed<RedisPoller>().hent(any(), any(), any())
        } returns MockInternalServerError.response

        val response = get(PATH)

        Assertions.assertEquals(HttpStatusCode.InternalServerError, response.status)

        val responseBody = response.bodyAsText().fromJson(String.serializer())
        Assertions.assertEquals(MockInternalServerError.responseBody, responseBody)
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
        .toBehovMap()
        .toJson(
            MapSerializer(
                BehovType.serializer(),
                AltinnOrganisasjon.serializer().set().løsning()
            )
        )
}

@OptIn(ExperimentalSerializationApi::class)
private object MockInternalServerError {
    const val responseBody = "uff da!"

    val response = responseBody.toLøsningFailure()
        .toBehovMap()
        .toJson(
            MapSerializer(
                BehovType.serializer(),
                NothingSerializer().løsning()
            )
        )
}

private fun <T : Any> T.toBehovMap(): Map<BehovType, T> =
    mapOf(
        BehovType.ARBEIDSGIVERE to this
    )
