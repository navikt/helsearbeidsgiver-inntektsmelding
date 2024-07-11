package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.fromEnv

class AltinnAppTest :
    FunSpec({
        val testRapid = spyk<TestRapid>()
        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("main") {
            mockkStatic("no.nav.helsearbeidsgiver.felles.EnvUtilsKt")
            mockkObject(RapidApplication)

            every { RapidApplication.create(any()) } returns testRapid

            every { "APP_URL".fromEnv() } returns "mocked_value"
            every { "ALTINN_SERVICE_CODE".fromEnv() } returns "mocked_value"
            every { "MASKINPORTEN_TOKEN_ENDPOINT".fromEnv() } returns "mocked_value"
            every { "MASKINPORTEN_ISSUER".fromEnv() } returns "mocked_value"
            every { "MASKINPORTEN_CLIENT_JWK".fromEnv() } returns "mocked_value"
            every { "MASKINPORTEN_CLIENT_ID".fromEnv() } returns "mocked_value"
            every { "ALTINN_SCOPE".fromEnv() } returns "mocked_value"
            every { "ALTINN_API_KEY".fromEnv() } returns "mocked_value"
            every { "KAFKA_BROKERS".fromEnv() } returns "mocked_value"

            main()
        }
    })
