package no.nav.helsearbeidsgiver.inntektsmelding.joark

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.hag.simba.utils.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.utils.test.date.juli

class TilDokumenterKtTest :
    FunSpec({
        test("lager dokumenter") {
            val mockInntektsmelding = mockInntektsmeldingV1()

            val dokumenter = tilDokumenter(mockInntektsmelding, 11.juli(2018))

            dokumenter.size shouldBe 1
            dokumenter[0].let {
                it.dokumentVarianter.size shouldBe 2
                it.dokumentVarianter[0].filtype shouldBe "XML"
                it.dokumentVarianter[1].filtype shouldBe "PDFA"
                it.tittel shouldBe "Inntektsmelding for sykepenger (endring) – 11.07.18"
            }
        }
    })
