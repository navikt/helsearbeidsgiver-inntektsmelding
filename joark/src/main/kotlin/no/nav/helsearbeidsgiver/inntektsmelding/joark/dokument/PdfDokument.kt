@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.ÅrsakInnsending
import no.nav.helsearbeidsgiver.pdf.PdfBuilder
import java.time.LocalDate

class PdfDokument(val dokument: InntektsmeldingDokument) {

    private val pdf = PdfBuilder(bodySize = 17)
    private var y = 0
    private val KOLONNE_EN = 0
    private val KOLONNE_TO = 420
    private val NATURALYTELSE_1 = KOLONNE_EN
    private val NATURALYTELSE_2 = KOLONNE_EN + 400
    private val NATURALYTELSE_3 = KOLONNE_EN + 700

    fun export(): ByteArray {
        addHeader()
        addAnsatt()
        addArbeidsgiver()
        addLine()
        addFraværsperiode()
        addLine()
        addInntekt()
        addLine()
        addRefusjon()
        addLine()
        addNaturalytelser()
        addLine()
        addTidspunkt()
        return pdf.export()
    }

    fun moveCursorBy(y: Int) {
        this.y += y
    }

    fun addLine() {
        moveCursorBy(pdf.bodySize)
        pdf.addLine(0, y)
        moveCursorBy(pdf.bodySize)
    }

    fun addSection(title: String) {
        pdf.addSection(title, KOLONNE_EN, y)
        moveCursorBy(pdf.sectionSize * 2)
    }

    fun addLabel(label: String, text: String? = null, x: Int = KOLONNE_EN, newY: Int = y, linefeed: Boolean = true) {
        pdf.addBold(label, x, newY)
        if (text != null) {
            pdf.addBody(text, x, newY + pdf.bodySize + (pdf.bodySize/2))
        }
        if (linefeed){
            moveCursorBy(if (text == null) {pdf.bodySize*2} else {pdf.bodySize*4})
        }
    }

    fun addText(text: String, x1: Int = KOLONNE_EN, y2: Int = y, linefeed: Boolean = true) {
        pdf.addBody(text, x1, y2)
        if (linefeed){
            moveCursorBy(pdf.bodySize*2)
        }
    }

    fun addHeader() {
        pdf.addTitle(
            title = when (dokument.årsakInnsending) {
                ÅrsakInnsending.ENDRING -> "Inntektsmelding for sykepenger - endring"
                else -> "Inntektsmelding for sykepenger"
            },
            x = 0,
            y = y
        )
        moveCursorBy(pdf.titleSize * 2)
    }

    fun addAnsatt() {
        addSection("Den ansatte")
        addLabel("Navn", dokument.fulltNavn, linefeed = false)
        addLabel("Personnummer", dokument.identitetsnummer, KOLONNE_TO)
    }

    fun addArbeidsgiver() {
        addSection("Arbeidsgiveren")
        addLabel("Virksomhetsnavn", dokument.virksomhetNavn, linefeed = false)
        addLabel("Organisasjonsnummer for underenhet", dokument.orgnrUnderenhet, KOLONNE_TO)
    }

    fun addFraværsperiode() { // TODO
        addSection("Fraværsperiode")
        val startY = y
        addLabel("Egenmelding")
        addPerioder(KOLONNE_EN, dokument.egenmeldingsperioder)
        addLabel("Fravær knyttet til sykmelding")
        addPerioder(KOLONNE_EN, dokument.fraværsperioder)
        val kolonne1max = y
        y = startY
        addLabel( "Bestemmende fraværsdag", "Bestemmende fraværsdag angir datoen som sykelønn skal beregnes ut i fra.", KOLONNE_TO)
        addLabel("Dato", dokument.bestemmendeFraværsdag.toNorsk(), KOLONNE_TO)
        addLabel(
            "Arbeidsgiverperiode",
            "Arbeidsgivers har ansvar vanligvis for å betale lønn",
            KOLONNE_TO
        )
        moveCursorBy(-pdf.bodySize)
        addText("til den sykemeldte under arbeidsgiverperioden", KOLONNE_TO)
        moveCursorBy(pdf.bodySize)
        addPerioder(KOLONNE_TO, dokument.arbeidsgiverperioder)
        val kolonne2max = y
        if (kolonne1max > kolonne2max) {
            y = kolonne1max
        } else {
            y = kolonne2max
        }
        moveCursorBy(pdf.bodySize*2)
    }

    fun addPerioder(x: Int, perioder: List<Periode>) {
        perioder.forEach {
            addLabel("Fra", it.fom.toNorsk(), x, linefeed = false)
            addLabel("Til", it.tom.toNorsk(), x + 200)
        }
    }

    fun addInntekt() {
        addSection("Beregnet månedslønn")
        addLabel("Registrert inntekt (per ${dokument.tidspunkt.toLocalDate().toNorsk()})", dokument.beregnetInntekt.toNorsk() + " kr/måned")
        val endringType = 1
        when (endringType) {
            1 -> addPermisjon()
            8 -> addFerie()
            2 -> addPermittering()
            3 -> addTariffendring()
            4 -> addVarigLonnsendring()
            5 -> addNyStilling()
            6 -> addNyStillingsprosent()
            7 -> addBonus()
        }
    }

    fun addInntektEndringPerioder(endringsårsak: String, perioder: List<Periode>){
        addLabel("Forklaring for endring", endringsårsak, linefeed = false)
        addPerioder(KOLONNE_TO, perioder)
    }

    fun addPermisjon(){
        val perioder = MockPerioder() // TODO
        addInntektEndringPerioder("Permisjon", perioder)
    }

    fun addFerie(){
        val perioder = MockPerioder() // TODO
        addInntektEndringPerioder("Ferie", perioder)
    }

    fun addPermittering(){
        val perioder = MockPerioder() // TODO
        addInntektEndringPerioder("Permittering", perioder)
    }

    fun addTariffendring(){
        val gjelderFra = LocalDate.now() // TODO
        val bleKjent = LocalDate.now() // TODO
        addLabel("Forklaring for endring", "Tariffendring")
        addLabel("Gjelder fra", gjelderFra.toNorsk())
        addLabel("Ble kjent", bleKjent.toNorsk())
    }

    fun addVarigLonnsendring(){
        val gjelderFra = LocalDate.now() // TODO
        addLabel("Forklaring for endring", "Varig lønnsendring")
        addLabel("Gjelder fra", gjelderFra.toNorsk(), KOLONNE_TO)
    }

    fun addNyStilling(){
        val gjelderFra = LocalDate.now() // TODO
        addLabel("Forklaring for endring", "Ny stilling")
        addLabel("Gjelder fra", gjelderFra.toNorsk(), KOLONNE_TO)
    }

    fun addNyStillingsprosent(){
        val gjelderFra = LocalDate.now() // TODO
        addLabel("Forklaring for endring", "Ny stillingsprosent")
        addLabel("Gjelder fra", gjelderFra.toNorsk(), KOLONNE_TO)
    }

    fun addBonus(){
        addLabel( "Forklaring for endring", "Bonus")
    }

    fun addRefusjon() {
        addSection("Refusjon")
        val full = dokument.fullLønnIArbeidsgiverPerioden
        val refusjon = dokument.refusjon
        addLabel("Betaler arbeidsgiver full lønn til arbeidstaker i arbeidsgiverperioden?", full.utbetalerFullLønn.toNorsk())
        addLabel("Begrunnelse", full.begrunnelse?.name ?: "-")
        addLabel("Utbetalt under arbeidsgiverperiode", (full.utbetalt?.toNorsk() ?: "-") + " kr")
        addLabel("Betaler arbeidsgiver lønn under hele eller deler av sykefraværet?", "Ja")
        addLabel("Refusjonsbeløp pr måned", (refusjon.refusjonPrMnd?.toNorsk() ?: "-") + " kr/måned") // Arbeidsgiver
        addLabel("Opphører refusjonskravet i perioden", refusjon.refusjonOpphører?.toNorsk() ?: "-")
        addLabel("Siste dag dere krever refusjon for", refusjon.refusjonOpphører?.toNorsk() ?: "-")
    }

    fun addNaturalytelser() {
        addSection("Bortfall av naturalytelser")
        val antallNaturalytelser = dokument.naturalytelser?.size ?: 0
        if (antallNaturalytelser == 0) {
            addLabel("Nei")
        } else {
            addLabel("Ja")
            addLabel("Naturalytelser", x = NATURALYTELSE_1, linefeed = false)
            addLabel("Dato naturalytelse bortfaller", x = NATURALYTELSE_2, linefeed = false)
            addLabel("Verdi naturalytelse - kr/måned", x = NATURALYTELSE_3)
            dokument.naturalytelser?.forEach {
                addText(it.naturalytelse.value, NATURALYTELSE_1, linefeed = false)
                addText(it.dato.toNorsk(), NATURALYTELSE_2, linefeed = false)
                addText(it.beløp.toNorsk(), NATURALYTELSE_3)
            }
            moveCursorBy(pdf.bodySize*2)
        }
    }

    fun addTidspunkt() {
        pdf.addItalics("Innsendt: ${dokument.tidspunkt.toNorsk()}", KOLONNE_EN, y)
        moveCursorBy(pdf.bodySize)
    }

}
