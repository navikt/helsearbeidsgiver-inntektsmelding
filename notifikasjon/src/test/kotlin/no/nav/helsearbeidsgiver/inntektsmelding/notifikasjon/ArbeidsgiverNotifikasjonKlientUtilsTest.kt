package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import io.kotest.core.spec.style.FunSpec
import io.kotest.data.row
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
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

        context("Generer riktig periode tekst for oppgave varsel tekst") {
            withData(
                mapOf(
                    "ingen perioder" to row(emptyList(), ""),
                    "en periode" to row(listOf(1.januar til 31.januar), " for periode: 01.01.2018 - 31.01.2018"),
                    "to perioder" to row(listOf(1.januar til 31.januar, 1.februar til 28.februar), " for periode: 01.01.2018 - [...] - 28.02.2018"),
                ),
            ) { (perioder, forventet) ->
                perioder.lesbarString() shouldBe forventet
            }
        }
    })
