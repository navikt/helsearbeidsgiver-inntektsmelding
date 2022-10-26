@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helsearbeidsgiver.pdf.PdfBuilder

class Kvittering {

    private val INDENT = 45

    fun export(): ByteArray {
        val b = PdfBuilder()
        b.addTitle("Kvittering - innsendt inntektsmelding", 0, 0)
        val kolonneTo = 420
        val ansatteY = 60
        b.addSection("Den ansatte", 0, ansatteY)
        lagLabel(b, 0, ansatteY + 47, "Navn", "Navn Navnesen")
        lagLabel(b, 420, ansatteY + 80, "Personnummer", "12345678901")
        val arbeidsgiverY = 190
        b.addSection("Arbeidsgiveren", 0, arbeidsgiverY)
        lagLabel(b, 0, arbeidsgiverY + 47, "Virksomhetsnavn", "Grunerløkka Pleiehjem")
        lagLabel(b, kolonneTo, arbeidsgiverY + 47, "Organisasjonsnummer for underenhet", "123456789")
        val fraværsperiodeY = 360
        b.addLine(0, fraværsperiodeY - 30)
        b.addSection("Fraværsperiode", 0, fraværsperiodeY)
        b.addBold("Egenmelding", 0, fraværsperiodeY + 35)
        lagPeriode(b, 0, fraværsperiodeY + 80, "01.10.2021", "06.10.2021")
        b.addBold("Fravær knyttet til sykmelding", 0, fraværsperiodeY + 145)
        val antalFravær = 3
        val fraværY = fraværsperiodeY + 180
        for (i in 1..antalFravær) {
            lagPeriode(b, 0, fraværY + (i - 1) * 60, "01.10.2021", "06.10.2021")
        }
        val bestemmendeX = 430
        val bestemmendeY = 420
        lagLabel(b, bestemmendeX, bestemmendeY, "Bestemmende fraværsdag", "Bestemmende fraværsdag angir datoen som sykelønn skal beregnes ut i fra.")
        lagLabel(b, bestemmendeX, bestemmendeY + 60, "Dato", "22.10.2021")
        lagLabel(
            b,
            bestemmendeX,
            bestemmendeY + 120,
            "Arbeidsgiverperiode",
            "Arbeidsgivers har ansvar vanligvis for å betale lønn til den sykemeldte under arbeidsgiverperioden"
        )
        lagPeriode(b, bestemmendeX, bestemmendeY + 180, "01.10.2021", "16.10.2021")
        val bruttoInntektY = fraværY + (antalFravær * 60) + 30
        b.addLine(0, bruttoInntektY)
        b.addSection("Bruttoinntekt siste 3 måneder", 0, bruttoInntektY + 30)
        b.addBold("Registrert inntekt (per 22.10.2021)", 0, bruttoInntektY + 60)
        b.addBody("42 000 kr/måned", 0, bruttoInntektY + 90)
        val refusjonY = bruttoInntektY + 180
        b.addLine(0, refusjonY - 30)
        b.addSection("Refusjon", 0, refusjonY)
        lagLabel(b, 0, refusjonY + 50, "Betaler arbeidsgiver full lønn til arbeidstaker i arbeidsgiverperioden?", "Nei")
        lagLabel(b, 0, refusjonY + 100, "Begrunnelse", "Jobbet kortere en måned")
        lagLabel(b, 0, refusjonY + 150, "Utbetalt under arbeidsgiverperiode", "0 kr")
        lagLabel(b, 0, refusjonY + 200, "Betaler arbeidsgiver lønn under hele eller deler av sykefraværet?", "Ja")
        lagLabel(b, 0, refusjonY + 250, "Refusjonsbeløp pr måned", "42 000 kr/måned")
        lagLabel(b, 0, refusjonY + 300, "Opphører refusjonskravet i perioden", "Ja")
        lagLabel(b, 0, refusjonY + 350, "Siste dag dere krever refusjon for", "03.01.2022")
        val naturalytelseY = refusjonY + 450
        val antallNaturalytelser = 10
        b.addLine(0, naturalytelseY - 30)
        b.addSection("Eventuelle naturalytelser", 0, naturalytelseY)
        if (antallNaturalytelser == 0) {
            b.addBody("Nei", 0, naturalytelseY + 30)
        } else {
            b.addBody("Ja", 0, naturalytelseY + 30)
            val kolonne1 = 0
            val kolonne2 = 150
            val kolonne3 = 385
            val tabellY = naturalytelseY + 60
            b.addBold("Naturalytelser", 0, tabellY)
            b.addBold("Dato naturalytelse bortfaller", kolonne2, tabellY)
            b.addBold("Verdi naturalytelse - kr/måned", kolonne3, tabellY)
            for (i in 1..antallNaturalytelser) {
                b.addBody("Fri transport", kolonne1, i * 30 + tabellY)
                b.addBody("03.01.2022", kolonne2, i * 30 + tabellY)
                b.addBody("1950", kolonne3, i * 30 + tabellY)
            }
        }
        val kvitteringY = naturalytelseY + ((antallNaturalytelser + 5) * 30) // 200
        b.addLine(0, kvitteringY)
        b.addFootnote("Kvittering - innsendt inntektsmelding - 12.05.2021 kl. 12.23", 0, kvitteringY + 30)
        return b.export()
    }

    fun lagLabel(b: PdfBuilder, x: Int, y: Int, label: String, text: String) {
        b.addBold(label, x, y)
        b.addBody(text, x, y + 20)
    }

    fun lagPeriode(b: PdfBuilder, x: Int = 0, y: Int, fom: String, tom: String) {
        b.addBold("Fra", x, y)
        b.addBold("Til", x + 182, y)
        b.addBody(fom, x, y + 26)
        b.addBody(tom, x + 182, y + 26)
    }

    fun export2(): ByteArray {
        return PdfBuilder()
            .addTitle("Kvittering - innsendt inntektsmelding", INDENT, 70)
            .addSection("Den ansatte", INDENT, 130)
            .addBold("Navn", INDENT, 177)
            .addBold("Personnummer", 476, 177)
            .addBody("Navn Navnesen", INDENT, 212)
            .addBody("12345678901", 476, 212)
            .addSection("Arbeidsgiveren", INDENT, 251)
            .addBold("Virksomhetsnavn", INDENT, 297)
            .addBold("Organisasjonsnummer for underenhet", 480, 297)
            .addBody("Grunerløkka Pleiehjem", INDENT, 332)
            .addBody("123456789", 480, 332)
            .addLine(INDENT, 387)
            .addSection("Fraværsperiode", INDENT, 414)
            .addBold("Egenmelding", INDENT, 453)
            .addBold("Fra", INDENT, 485)
            .addBold("Til", 182, 485)
            .addBody("01.10.2021", INDENT, 511)
            .addBody("06.10.2021", 182, 511)
            .addBold("Fravær knyttet til sykmelding", INDENT, 550)
            .addBold("Fra", INDENT, 580)
            .addBold("Til", 182, 580)
            .addBody("22.10.2021", INDENT, 607)
            .addBody("28.10.2021", 182, 607)
            .addBold("Fra", INDENT, 650)
            .addBold("Til", 182, 650)
            .addBody("07.10.2021", INDENT, 675)
            .addBody("20.10.2021", 182, 675)
            .addBold("Bestemmende fraværsdag", 476, 420)
            .addBody("Bestemmende fraværsdag angir datoen som sykelønn skal beregnes ut i fra.", 476, 445)
            .addBold("Dato", 476, 485)
            .addBody("22.10.2021", 476, 511)
            .addBold("Arbeidsgiverperiode", 476, 550)
            .addBody("Arbeidsgivers har ansvar vanligvis for å betale lønn til", 476, 580)
            .addBody("den sykemeldte under arbeidsgiverperioden", 476, 607)
            .addBold("Fra", 476, 650)
            .addBold("Til", 645, 650)
            .addBody("01.10.2021", 476, 675)
            .addBody("16.10.2021", 645, 675)
            .addLine(INDENT, 723)
            .addSection("Bruttoinntekt siste 3 måneder", INDENT, 744)
            .addBold("Registrert inntekt (per 22.10.2021)", INDENT, 790)
            .addBody("42 000 kr/måned", INDENT, 820)
            .addLine(INDENT, 874)
            .addSection("Refusjon", INDENT, 891)
            .addBold("Betaler arbeidsgiver full lønn til arbeidstaker i arbeidsgiverperioden?", INDENT, 920)
            .addBody("Nei", INDENT, 940)
            .addBold("Begrunnelse", INDENT, 968)
            .addBody("Jobbet kortere en måned", INDENT, 990)
            .addBold("Utbetalt under arbeidsgiverperiode", INDENT, 1040)
            .addBody("0 kr", INDENT, 1060)
            .addBold("Betaler arbeidsgiver lønn under hele eller deler av sykefraværet?", INDENT, 1122)
            .addBody("Ja", INDENT, 1149)
            .addBold("Refusjonsbeløp pr måned", INDENT, 1182)
            .addBody("42 000 kr/måned", INDENT, 1206)
            .addBold("Opphører refusjonskravet i perioden", INDENT, 1240)
            .addBody("Ja", INDENT, 1269)
            .addBold("Siste dag dere krever refusjon for", INDENT, 1300)
            .addBody("03.01.2022", INDENT, 1321)
            .addLine(INDENT, 1374)
            .addSection("Eventuelle naturalytelser", INDENT, 1395)
            .addBody("Ja", INDENT, 1436)
            .addBold("Naturalytelser", INDENT, 1473)
            .addBold("Dato naturalytelse bortfaller", 196, 1473)
            .addBold("Verdi naturalytelse - kr/måned", 430, 1473)
            .addBody("Fri transport", INDENT, 1502)
            .addBody("03.01.2022", 196, 1502)
            .addBody("1950", 430, 1502)
            .addBody("Mobiltelefon", INDENT, 1527)
            .addBody("03.01.2022", 196, 1527)
            .addBody("950", 430, 1527)
            .addLine(INDENT, 1584)
            .addFootnote("Kvittering - innsendt inntektsmelding - 12.05.2021 kl. 12.23", INDENT, 1606)
            .export()
    }
}
