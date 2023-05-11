@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Bonus
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Ferie
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NyStilling
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NyStillingsprosent
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Permisjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Permittering
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Tariffendring
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.VarigLonnsendring
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.ÅrsakInnsending
import no.nav.helsearbeidsgiver.pdf.PdfBuilder
import java.time.LocalDate

class PdfDokument(val dokument: InntektsmeldingDokument) {

    private val pdf = PdfBuilder(bodySize = 20) // Setter skriftstørrelsen på labels og text
    private var y = 0
    private val KOLONNE_EN = 0
    private val KOLONNE_TO = 420
    private val NATURALYTELSE_1 = KOLONNE_EN
    private val NATURALYTELSE_2 = KOLONNE_EN + 400
    private val NATURALYTELSE_3 = KOLONNE_EN + 700
    private val BOLD_LABELS = false // Bestemmer om navnet på label eller verdien skal stå i bold

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

    fun moveCursorTo(y: Int) {
        this.y = y
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
        pdf.addText(label, x, newY, BOLD_LABELS)
        if (text != null) {
            pdf.addText(text, x, newY + pdf.bodySize + (pdf.bodySize / 2), !BOLD_LABELS)
        }
        if (linefeed) {
            moveCursorBy(if (text == null) { pdf.bodySize * 2 } else { pdf.bodySize * 4 })
        }
    }

    fun addText(text: String, x1: Int = KOLONNE_EN, y2: Int = y, linefeed: Boolean = true) {
        pdf.addText(text, x1, y2, !BOLD_LABELS)
        if (linefeed) {
            moveCursorBy(pdf.bodySize * 2)
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
        // pdf.addImage("logo.svg", 500, y)
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

    fun addFraværsperiode() {
        addSection("Fraværsperiode")
        val seksjonStartY = y // Husk når denne seksjonen starter i y-aksen

        // --- Kolonnen til venstre -------------------------------------------------
        addLabel("Bestemmende fraværsdag (skjæringstidpunkt)", dokument.bestemmendeFraværsdag.toNorsk(), KOLONNE_EN)
        addLabel("Arbeidsgiverperiode", x = KOLONNE_EN)
        addPerioder(KOLONNE_EN, dokument.arbeidsgiverperioder)

        // Husk maks høyden på venstre side
        val kolonneVenstreMaxY = y

        // --- Kolonnen til høyre ---------------------------------------------------
        moveCursorTo(seksjonStartY) // Gjenopprett y-aksen fra tidligere
        addLabel("Egenmelding", x = KOLONNE_TO)
        addPerioder(KOLONNE_TO, dokument.egenmeldingsperioder)
        addLabel("Sykemeldingsperioder", x = KOLONNE_TO)
        addPerioder(KOLONNE_TO, dokument.fraværsperioder)

        // Husk maks høyden på høyre side
        val kolonneHøyreMaxY = y

        // --- Finn ut hvilken kolonne som ble høyest -------------------------------
        val maksKolonneY = if (kolonneVenstreMaxY > kolonneHøyreMaxY) { kolonneVenstreMaxY } else { kolonneHøyreMaxY }
        moveCursorTo(maksKolonneY)
        moveCursorBy(pdf.bodySize * 2)
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
        val endringsårsak = dokument.inntekt?.endringÅrsak
        when (endringsårsak) {
            is Permisjon -> addPermisjon(endringsårsak)
            is Ferie -> addFerie(endringsårsak)
            is Permittering -> addPermittering(endringsårsak)
            is Tariffendring -> addTariffendring(endringsårsak)
            is VarigLonnsendring -> addVarigLonnsendring(endringsårsak)
            is NyStilling -> addNyStilling(endringsårsak)
            is NyStillingsprosent -> addNyStillingsprosent(endringsårsak)
            is Bonus -> addBonus(endringsårsak)
            else -> {}
        }
    }

    fun addInntektEndringPerioder(endringsårsak: String, perioder: List<Periode>) {
        addLabel("Forklaring for endring", endringsårsak, linefeed = false)
        addPerioder(KOLONNE_TO, perioder)
    }

    fun addPermisjon(permisjon: Permisjon) {
        addInntektEndringPerioder("Permisjon", permisjon.liste)
    }

    fun addFerie(ferie: Ferie) {
        addInntektEndringPerioder("Ferie", ferie.liste)
    }

    fun addPermittering(permittering: Permittering) {
        addInntektEndringPerioder("Permittering", permittering.liste)
    }

    fun addTariffendring(tariffendring: Tariffendring) {
        addLabel("Forklaring for endring", "Tariffendring")
        addLabel("Gjelder fra", tariffendring.gjelderFra.toNorsk(), linefeed = false)
        addLabel("Ble kjent", tariffendring.bleKjent.toNorsk(), KOLONNE_TO)
    }

    fun addVarigLonnsendring(varigLonnsendring: VarigLonnsendring) {
        addLabel("Forklaring for endring", "Varig lønnsendring")
        addLabel("Gjelder fra", varigLonnsendring.gjelderFra.toNorsk())
    }

    fun addNyStilling(nyStilling: NyStilling) {
        addLabel("Forklaring for endring", "Ny stilling", linefeed = false)
        addLabel("Gjelder fra", nyStilling.gjelderFra.toNorsk(), KOLONNE_TO)
    }

    fun addNyStillingsprosent(nyStillingsprosent: NyStillingsprosent) {
        addLabel("Forklaring for endring", "Ny stillingsprosent", linefeed = false)
        addLabel("Gjelder fra", nyStillingsprosent.gjelderFra.toNorsk(), KOLONNE_TO)
    }

    fun addBonus(bonus: Bonus) {
        val årligBonus = 0.toBigDecimal() // TODO Må bruke bonus fra datamodell
        val datoBonus = LocalDate.now() // TODO Må bruke dato fra datamodell
        addLabel("Forklaring for endring", "Bonus")
        //addLabel("Estimert årlig bonus", årligBonus.toNorsk())
        //addLabel("Dato siste bonus", datoBonus.toNorsk())
    }

    fun addRefusjon() {
        addSection("Refusjon")

        val lønnArbeidsgiverperioden = dokument.fullLønnIArbeidsgiverPerioden
        addLabel("Betaler arbeidsgiver full lønn til arbeidstaker i arbeidsgiverperioden?", lønnArbeidsgiverperioden.utbetalerFullLønn.toNorsk())
        if (lønnArbeidsgiverperioden.utbetalerFullLønn) {
            // Ja
        } else {
            // Nei - to ekstra spørsmål
            addLabel("Begrunnelse", lønnArbeidsgiverperioden.begrunnelse?.name ?: "-")
            addLabel("Utbetalt under arbeidsgiverperiode", (lønnArbeidsgiverperioden.utbetalt?.toNorsk() ?: "-") + " kr")
        }

        val refusjon = dokument.refusjon
        addLabel("Betaler arbeidsgiver lønn under hele eller deler av sykefraværet?", refusjon.utbetalerHeleEllerDeler.toNorsk())

        if (dokument.refusjon.utbetalerHeleEllerDeler) {
            // Ja - tre ekstra spørsmål
            addLabel("Refusjonsbeløp pr måned", (refusjon.refusjonPrMnd?.toNorsk() ?: "-") + " kr/måned") // Arbeidsgiver

            val opphørerKravet = refusjon.refusjonOpphører != null
            addLabel("Opphører refusjonskravet i perioden", opphørerKravet.toNorsk())
            if (opphørerKravet) {
                addLabel("Siste dag dere krever refusjon for", refusjon.refusjonOpphører?.toNorsk() ?: "-")
            }

            val endringer = dokument.refusjon.refusjonEndringer ?: emptyList()
            addLabel("Endringer i refusjon i perioden", (!endringer.isEmpty()).toNorsk())
            if (endringer.isEmpty()) {
                // Nei - Ingen endringer i perioden
            } else {
                // Ja - endringer
                endringer.forEach {
                    addLabel("Beløp", it.beløp?.toNorsk() ?: "-", KOLONNE_EN, linefeed = false)
                    addLabel("Dato", it.dato?.toNorsk() ?: "-", KOLONNE_TO)
                }
            }
        } else {
            // Nei - ingen flere spørsmål
        }
    }

    fun addNaturalytelser() {
        addSection("Bortfall av naturalytelser")
        val antallNaturalytelser = dokument.naturalytelser?.size ?: 0
        if (antallNaturalytelser == 0) {
            addLabel("Nei")
        } else {
            addLabel("Naturalytelser", x = NATURALYTELSE_1, linefeed = false)
            addLabel("Dato naturalytelse bortfaller", x = NATURALYTELSE_2, linefeed = false)
            addLabel("Verdi naturalytelse - kr/måned", x = NATURALYTELSE_3)
            dokument.naturalytelser?.forEach {
                addText(it.naturalytelse.value, NATURALYTELSE_1, linefeed = false)
                addText(it.dato.toNorsk(), NATURALYTELSE_2, linefeed = false)
                addText(it.beløp.toNorsk(), NATURALYTELSE_3)
            }
            moveCursorBy(pdf.bodySize * 2)
        }
    }

    fun addTidspunkt() {
        pdf.addItalics("Innsendt: ${dokument.tidspunkt.toNorsk()}", KOLONNE_EN, y)
        moveCursorBy(pdf.bodySize)
    }
}
