package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr

class ArbeidsgiverNotifikasjonKlientUtilsTest :
    FunSpec({

        context(NotifikasjonTekst::sakTittel.name) {
            test("fnr gir forventet saktittel") {
                val person = Person(Fnr("31030267462"), "James Bånn")

                NotifikasjonTekst.sakTittel(person) shouldBe "Inntektsmelding for James Bånn: f. 310302"
            }

            test("dnr gir forventet saktittel") {
                val person = Person(Fnr("63047505900"), "Pierce Brosjan")

                NotifikasjonTekst.sakTittel(person) shouldBe "Inntektsmelding for Pierce Brosjan: f. 230475"
            }
        }
    })
