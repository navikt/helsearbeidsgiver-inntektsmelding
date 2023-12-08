package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.aareg.Ansettelsesperiode
import no.nav.helsearbeidsgiver.aareg.Arbeidsforhold
import no.nav.helsearbeidsgiver.aareg.Arbeidsgiver
import no.nav.helsearbeidsgiver.aareg.Opplysningspliktig
import no.nav.helsearbeidsgiver.aareg.Periode
import no.nav.helsearbeidsgiver.altinn.AltinnOrganisasjon
import no.nav.helsearbeidsgiver.brreg.Virksomhet
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.pdl.domene.FullPerson
import no.nav.helsearbeidsgiver.pdl.domene.PersonNavn
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.kl
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AktiveOrgnrServiceIT : EndToEndTest() {

    @Test
    fun `Test hente aktive organisasjoner`() {
        coEvery { aaregClient.hentArbeidsforhold(any(), any()) } returns Mock.arbeidsforholdListe
        coEvery { altinnClient.hentRettighetOrganisasjoner(any()) } returns Mock.altinnOrganisasjonSet
        coEvery { brregClient.hentVirksomhetNavn("810007842") } returns "ANSTENDIG PIGGSVIN BARNEHAGE"
        coEvery { brregClient.hentVirksomheter(any()) } returns listOf(Virksomhet(organisasjonsnummer = "810007842", navn = "ANSTENDIG PIGGSVIN BARNEHAGE"))
        coEvery { pdlKlient.personBolk(any()) } returns listOf(
            FullPerson(navn = PersonNavn(fornavn = "Bjarne", mellomnavn = null, etternavn = "Betjent"), foedselsdato = 1.januar, ident = Mock.FNR)
        )
        publish(
            Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
            Key.CLIENT_ID to Mock.clientId.toJson(),
            Key.FNR to Mock.FNR.toJson(),
            Key.ARBEIDSGIVER_FNR to Mock.FNR_AG.toJson()
        )

        Thread.sleep(15000)

        redisStore.get(RedisKey.of(Mock.clientId)) shouldBe Mock.GYLDIG_AKTIVE_ORGNR_RESPONSE

        messages.filter(EventName.AKTIVE_ORGNR_REQUESTED).filter(BehovType.ARBEIDSGIVERE).firstAsMap()[Key.IDENTITETSNUMMER]?.fromJson(String.serializer()) shouldBe Mock.FNR_AG

        messages.filter(EventName.AKTIVE_ORGNR_REQUESTED).filter(BehovType.ARBEIDSFORHOLD).firstAsMap()[Key.IDENTITETSNUMMER]?.fromJson(String.serializer()) shouldBe Mock.FNR

        messages.filter(EventName.AKTIVE_ORGNR_REQUESTED).filter(BehovType.FULLT_NAVN).firstAsMap()[Key.IDENTITETSNUMMER]?.fromJson(String.serializer()) shouldBe Mock.FNR

        // messages.filter(EventName.AKTIVE_ORGNR_REQUESTED).filter(Key.ARBEIDSFORHOLD).firstAsMap()[Key.ARBEIDSFORHOLD] shouldBe Mock.FNR

        messages.filter(EventName.AKTIVE_ORGNR_REQUESTED).filter(Key.ORG_RETTIGHETER).firstAsMap()[Key.ORG_RETTIGHETER]?.fromJson(String.serializer().set()) shouldBe Mock.altinnOrganisasjonSet.map { it.orgnr }.toSet()
    }

    @Test
    fun `Aareg kall feiler`() {
        coEvery { aaregClient.hentArbeidsforhold(any(), any()) } returns emptyList()
        coEvery { altinnClient.hentRettighetOrganisasjoner(any()) } returns Mock.altinnOrganisasjonSet
        coEvery { brregClient.hentVirksomhetNavn("810007842") } returns "ANSTENDIG PIGGSVIN BARNEHAGE"
        coEvery { brregClient.hentVirksomheter(any()) } returns listOf(Virksomhet(organisasjonsnummer = "810007842", navn = "ANSTENDIG PIGGSVIN BARNEHAGE"))
        publish(
            Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
            Key.CLIENT_ID to Mock.clientId.toJson(),
            Key.FNR to Mock.FNR.toJson(),
            Key.ARBEIDSGIVER_FNR to Mock.FNR_AG.toJson()
        )

        Thread.sleep(15000)

        redisStore.get(RedisKey.of(Mock.clientId)) shouldBe Mock.FEILTET_AKTIVE_ORGNR_RESPONSE
    }

    private object Mock {

        val GYLDIG_AKTIVE_ORGNR_RESPONSE = """
            {
                "fulltNavn": "Bjarne Betjent",
                "underenheter": [{"orgnrUnderenhet": "810007842", "virksomhetsnavn": "ANSTENDIG PIGGSVIN BARNEHAGE"}]
            }
        """.removeJsonWhitespace()
        val FEILTET_AKTIVE_ORGNR_RESPONSE = """
            {
                "underenheter": [],
                "feilReport": {
                    "feil": [{"melding": "Fant ingen aktive arbeidsforhold"}]
                 }
            }
        """.removeJsonWhitespace()
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
                        tom = null
                    )
                ),
                registrert = 3.januar.kl(6, 30, 40, 50000)
            )
        )

        val altinnOrganisasjonSet =
            setOf(
                AltinnOrganisasjon(
                    navn = "ANSTENDIG PIGGSVIN BYDEL",
                    type = "organisasjon",
                    orgnrHovedenhet = "810007702"
                ),
                AltinnOrganisasjon(
                    navn = "ANSTENDIG PIGGSVIN BARNEHAGE",
                    type = "organisasjon",
                    orgnr = "810007842",
                    orgnrHovedenhet = "810007702"

                ),
                AltinnOrganisasjon(
                    navn = "ANSTENDIG PIGGSVIN BRANNVESEN",
                    type = "organisasjon",
                    orgnr = "810008032",
                    orgnrHovedenhet = "810007702"

                ),
                AltinnOrganisasjon(
                    navn = "ANSTENDIG PIGGSVIN SYKEHJEM",
                    type = "organisasjon",
                    orgnr = "810007982",
                    orgnrHovedenhet = "810007702"
                )
            )
    }
}
