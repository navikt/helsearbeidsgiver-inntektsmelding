package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import io.kotest.core.spec.style.FunSpec
import io.kotest.data.row
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.utils.tilKortFormat
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

class ArbeidsgiverNotifikasjonKlientUtilsTest :
    FunSpec({
        val defaultFnr = Fnr("31030267462")
        context(NotifikasjonTekst::sakTittel.name) {
            test("fnr gir forventet saktittel") {
                val person = Person(defaultFnr, "James Bånn")
                NotifikasjonTekst.sakTittel(Inntektsmelding.Type.Forespurt(UUID.randomUUID()), person) shouldBe "Inntektsmelding for James Bånn: f. 310302"
            }

            test("dnr gir forventet saktittel") {
                val person = Person(Fnr("63047505900"), "Pierce Brosjan")
                NotifikasjonTekst.sakTittel(Inntektsmelding.Type.Forespurt(UUID.randomUUID()), person) shouldBe "Inntektsmelding for Pierce Brosjan: f. 230475"
            }

            test("Selvbestemt gir forventet saktittel med navn") {
                val person = Person(defaultFnr, "Sean Kånneri")
                NotifikasjonTekst.sakTittel(Inntektsmelding.Type.Selvbestemt(UUID.randomUUID()), person) shouldBe "Inntektsmelding for Sean Kånneri: f. 310302"
            }

            test("Fisker gir forventet saktittel uten navn") {
                val person = Person(defaultFnr, "Daniel Kraig")
                NotifikasjonTekst.sakTittel(Inntektsmelding.Type.Fisker(UUID.randomUUID()), person) shouldBe "Inntektsmelding for: f. 310302"
            }

            test("UtenArbeidsforhold gir forventet saktittel uten navn") {
                val person = Person(defaultFnr, "Roger Mår")
                NotifikasjonTekst.sakTittel(Inntektsmelding.Type.UtenArbeidsforhold(UUID.randomUUID()), person) shouldBe "Inntektsmelding for: f. 310302"
            }
        }

        context("Generer riktig periode tekst for oppgave varsel tekst") {
            withData(
                mapOf(
                    "en periode" to row(listOf(1.januar til 31.januar), "01.01.2018 - 31.01.2018"),
                    "to perioder" to row(listOf(1.januar til 31.januar, 1.februar til 28.februar), "01.01.2018 - [...] - 28.02.2018"),
                ),
            ) { (perioder, forventet) ->
                perioder.tilKortFormat() shouldBe forventet
            }
        }
    })
