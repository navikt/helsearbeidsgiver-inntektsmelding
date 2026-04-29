package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerifySequence
import kotlinx.serialization.builtins.serializer
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Ansettelsesforhold
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.juli
import no.nav.helsearbeidsgiver.utils.test.date.mai
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import no.nav.helsearbeidsgiver.aareg.Ansettelsesforhold as KlientAnsettelsesforhold

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HentArbeidsforholdSelvbestemtIT : EndToEndTest() {
    @Test
    fun `henter arbeidsforhold`() {
        val kontekstId = UUID.randomUUID()
        val orgnr = Orgnr.genererGyldig()
        val sykmeldtFnr = Fnr.genererGyldig()
        val periode = 15.april til 12.august
        val expectedAnsettelsesforhold =
            setOf(
                Ansettelsesforhold(
                    startdato = 11.april,
                    sluttdato = 11.mai,
                    yrkesKode = "1234567",
                    yrkesBeskrivelse = "BARNEHAGEASSISTENT",
                    stillingsprosent = 100.0,
                ),
                Ansettelsesforhold(
                    startdato = 20.april,
                    sluttdato = 22.april,
                    yrkesKode = "3456789",
                    yrkesBeskrivelse = "HJELPEPLEIER",
                    stillingsprosent = 50.0,
                ),
                Ansettelsesforhold(
                    startdato = 18.mai,
                    sluttdato = null,
                    yrkesKode = "7654321",
                    yrkesBeskrivelse = "SYKEPLEIER",
                    stillingsprosent = 80.0,
                ),
            )
        val expectedAnsettelsesforholdPerOrgnr =
            mapOf(
                orgnr to
                    setOf(
                        KlientAnsettelsesforhold(
                            startdato = 11.april,
                            sluttdato = 11.mai,
                            yrkesKode = "1234567",
                            yrkesBeskrivelse = "BARNEHAGEASSISTENT",
                            stillingsprosent = 100.0,
                        ),
                        KlientAnsettelsesforhold(
                            startdato = 20.april,
                            sluttdato = 22.april,
                            yrkesKode = "3456789",
                            yrkesBeskrivelse = "HJELPEPLEIER",
                            stillingsprosent = 50.0,
                        ),
                        KlientAnsettelsesforhold(
                            startdato = 18.mai,
                            sluttdato = null,
                            yrkesKode = "7654321",
                            yrkesBeskrivelse = "SYKEPLEIER",
                            stillingsprosent = 80.0,
                        ),
                    ),
                Orgnr.genererGyldig() to
                    setOf(
                        KlientAnsettelsesforhold(
                            startdato = 1.juli,
                            sluttdato = 31.juli,
                            yrkesKode = "2345678",
                            yrkesBeskrivelse = "RENHOLDER",
                            stillingsprosent = 60.0,
                        ),
                        KlientAnsettelsesforhold(
                            startdato = 2.august,
                            sluttdato = null,
                            yrkesKode = "8765432",
                            yrkesBeskrivelse = "VAKTMESTER",
                            stillingsprosent = 70.0,
                        ),
                    ),
            )

        coEvery { aaregClient.hentAnsettelsesforhold(sykmeldtFnr.verdi, kontekstId.toString()) } returns expectedAnsettelsesforholdPerOrgnr

        publish(
            Key.EVENT_NAME to EventName.HENT_ARBEIDSFORHOLD_SELVBESTEMT_REQUESTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.ORGNR_UNDERENHET to orgnr.toJson(),
                    Key.SYKMELDT_FNR to sykmeldtFnr.toJson(),
                    Key.PERIODE to periode.toJson(Periode.serializer()),
                ).toJson(),
        )

        // Ingen feil
        messages.filterFeil().all().shouldBeEmpty()

        // Forventede klientkall
        coVerifySequence {
            aaregClient.hentAnsettelsesforhold(sykmeldtFnr.verdi, kontekstId.toString())
            aaregClient.hentAnsettelsesperioder(sykmeldtFnr.verdi, kontekstId.toString())
        }

        // Forventet respons lagret i Redis
        val redisResponse =
            redisConnection
                .get(RedisPrefix.HentArbeidsforholdSelvbestemt, kontekstId)
                .shouldNotBeNull()
                .fromJson(ResultJson.serializer())

        redisResponse.success.shouldNotBeNull().fromJson(Ansettelsesforhold.serializer().set()) shouldBe expectedAnsettelsesforhold
        redisResponse.failure.shouldBeNull()
    }

    @Test
    fun `feil i klient lagres som respons i Redis`() {
        val kontekstId = UUID.randomUUID()
        val orgnr = Orgnr.genererGyldig()
        val sykmeldtFnr = Fnr.genererGyldig()
        val periode = 15.april til 12.mai

        coEvery { aaregClient.hentAnsettelsesperioder(sykmeldtFnr.verdi, kontekstId.toString()) } throws NullPointerException()

        publish(
            Key.EVENT_NAME to EventName.HENT_ARBEIDSFORHOLD_SELVBESTEMT_REQUESTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.ORGNR_UNDERENHET to orgnr.toJson(),
                    Key.SYKMELDT_FNR to sykmeldtFnr.toJson(),
                    Key.PERIODE to periode.toJson(Periode.serializer()),
                ).toJson(),
        )

        // Forventede feil
        messages.filterFeil().all().size shouldBeExactly 1

        // Forventede klientkall
        coVerifySequence {
            aaregClient.hentAnsettelsesforhold(sykmeldtFnr.verdi, kontekstId.toString())
            aaregClient.hentAnsettelsesperioder(sykmeldtFnr.verdi, kontekstId.toString())
        }

        // Forventet respons lagret i Redis
        val redisResponse =
            redisConnection
                .get(RedisPrefix.HentArbeidsforholdSelvbestemt, kontekstId)
                .shouldNotBeNull()
                .fromJson(ResultJson.serializer())

        redisResponse.success.shouldBeNull()
        redisResponse.failure.shouldNotBeNull().fromJson(String.serializer()) shouldBe "Klarte ikke hente ansettelsesperioder fra Aareg."
    }
}
