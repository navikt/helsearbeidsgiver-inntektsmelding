package no.nav.helsearbeidsgiver.inntektsmelding.joark

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.hag.simba.utils.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.api.AvsenderSystem
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class TittelUtilsKtTest :
    FunSpec({
        test("tittel for _ny_ inntektsmelding") {
            val im = mockInntektsmeldingV1().copy(aarsakInnsending = AarsakInnsending.Ny)
            im.tittel() shouldBe "Inntektsmelding for sykepenger"
        }

        test("tittel for _ny_ inntektsmelding med typetillegg") {
            val im =
                mockInntektsmeldingV1().copy(
                    type = Inntektsmelding.Type.Behandlingsdager(UUID.randomUUID()),
                    aarsakInnsending = AarsakInnsending.Ny,
                )
            im.tittel() shouldBe "Inntektsmelding for sykepenger (behandlingsdager)"
        }

        test("tittel for _endret_ inntektsmelding") {
            val im = mockInntektsmeldingV1().copy(aarsakInnsending = AarsakInnsending.Endring)
            im.tittel() shouldBe "Inntektsmelding for sykepenger (endring)"
        }

        test("tittel for _endret_ inntektsmelding med typetillegg") {
            val im =
                mockInntektsmeldingV1().copy(
                    type = Inntektsmelding.Type.Behandlingsdager(UUID.randomUUID()),
                    aarsakInnsending = AarsakInnsending.Endring,
                )
            im.tittel() shouldBe "Inntektsmelding for sykepenger (endring, behandlingsdager)"
        }

        context("tittel lages med ulike tillegg basert på inntektsmeldingstype") {
            val id = UUID.randomUUID()
            val avsenderSystem = AvsenderSystem(Orgnr.genererGyldig(), "TestSys", "1.0")

            withData(
                nameFn = { (imType, _) -> imType::class.simpleName.orEmpty() },
                listOf(
                    Inntektsmelding.Type.Selvbestemt(id) to null,
                    Inntektsmelding.Type.Fisker(id) to ", fisker med hyre",
                    Inntektsmelding.Type.UtenArbeidsforhold(id) to ", unntatt registrering i Aa-registeret",
                    Inntektsmelding.Type.Behandlingsdager(id) to ", behandlingsdager",
                    Inntektsmelding.Type.Forespurt(id, true) to null,
                    Inntektsmelding.Type.Forespurt(id, false) to ", arbeidsgiverperiode – ikke forespurt",
                    Inntektsmelding.Type.ForespurtEkstern(id, true, avsenderSystem) to null,
                    Inntektsmelding.Type.ForespurtEkstern(id, false, avsenderSystem) to ", arbeidsgiverperiode – ikke forespurt",
                ),
            ) { (imType, forventetTillegg) ->
                val beskrivelse = mockInntektsmeldingV1().copy(type = imType).tittel()
                val forventetBeskrivelse = "Inntektsmelding for sykepenger (endring${forventetTillegg.orEmpty()})"

                beskrivelse shouldBe forventetBeskrivelse
            }
        }
    })
