package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helsearbeidsgiver.pdf.PdfBuilder

class Kvittering {

    private val INDENT = 45

    fun export(): ByteArray {
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
