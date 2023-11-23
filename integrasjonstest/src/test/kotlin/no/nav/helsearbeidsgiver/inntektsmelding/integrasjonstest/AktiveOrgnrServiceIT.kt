package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import no.nav.helsearbeidsgiver.aareg.Ansettelsesperiode
import no.nav.helsearbeidsgiver.aareg.Arbeidsforhold
import no.nav.helsearbeidsgiver.aareg.Arbeidsgiver
import no.nav.helsearbeidsgiver.aareg.Opplysningspliktig
import no.nav.helsearbeidsgiver.aareg.Periode
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.kl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AktiveOrgnrServiceIT : EndToEndTest() {

    @Test
    fun `Test hente aktive organisasjoner`() {
        coEvery { aaregClient.hentArbeidsforhold(any(), any()) } returns Mock.arbeidsforholdListe
        publish(
            Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
            Key.CLIENT_ID to Mock.clientId.toJson(),
            DataFelt.FNR to Mock.FNR.toJson(),
            DataFelt.ARBEIDSGIVER_FNR to Mock.FNR_AG.toJson()
        )

        Thread.sleep(10000)

        messages.filter(EventName.AKTIVE_ORGNR_REQUESTED)
        redisStore.get(RedisKey.of(Mock.clientId)) shouldNotBe null
    }

    private object Mock {
        const val ORGNR = "stolt-krakk"
        const val FNR = "kongelig-albatross"
        const val FNR_AG = "uutgrunnelig-koffert"

        val clientId = randomUuid()

        val arbeidsforholdListe = listOf(
            Arbeidsforhold(
                arbeidsgiver = Arbeidsgiver(
                    type = "Underenhet",
                    organisasjonsnummer = "810007842"
                ),
                opplysningspliktig = Opplysningspliktig(
                    type = "ikke brukt",
                    organisasjonsnummer = "ikke brukt heller"
                ),
                arbeidsavtaler = emptyList(),
                ansettelsesperiode = Ansettelsesperiode(
                    Periode(
                        fom = 1.januar,
                        tom = 16.januar
                    )
                ),
                registrert = 3.januar.kl(6, 30, 40, 50000)
            )
        )
    }
}
