@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Bonus
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Feilregistrert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Ferie
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Ferietrekk
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.NyStilling
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.NyStillingsprosent
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Nyansatt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Permisjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Permittering
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Sykefravaer
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Tariffendring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.VarigLonnsendring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import java.time.LocalDate

private const val FORKLARING_ENDRING = "Forklaring for endring"
class PdfDokument(val dokument: Inntektsmelding) {

    private val pdf = PdfBuilder(bodySize = 20, topText = "Innsendt: ${dokument.tidspunkt.toNorsk()}") // Setter skriftstørrelsen på labels og text
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

    private fun moveCursorBy(y: Int) {
        this.y += y
    }

    private fun moveCursorTo(y: Int) {
        this.y = y
    }

    private fun addLine() {
        moveCursorBy(pdf.bodySize)
        pdf.addLine(0, y)
        moveCursorBy(pdf.bodySize)
    }

    private fun addSection(title: String) {
        pdf.addSection(title, KOLONNE_EN, y)
        moveCursorBy(pdf.sectionSize * 2)
    }

    private fun addLabel(
        label: String,
        text: String? = null,
        x: Int = KOLONNE_EN,
        newY: Int = y,
        linefeed: Boolean = true,
        splitLines: Boolean = false
    ) {
        pdf.addText(label, x, newY, BOLD_LABELS)
        if (splitLines && text != null) {
            text.delOppLangeNavn().forEach {
                pdf.addText(it, x, y + pdf.bodySize + (pdf.bodySize / 2), !BOLD_LABELS)
                moveCursorBy(pdf.bodySize * 2)
            }
        } else {
            if (text != null) {
                pdf.addText(text, x, newY + pdf.bodySize + (pdf.bodySize / 2), !BOLD_LABELS)
            }
            if (linefeed) {
                moveCursorBy(if (text == null) { pdf.bodySize * 2 } else { pdf.bodySize * 4 })
            }
        }
    }

    private fun addText(text: String, x1: Int = KOLONNE_EN, y2: Int = y, linefeed: Boolean = true) {
        pdf.addText(text, x1, y2, !BOLD_LABELS)
        if (linefeed) {
            moveCursorBy(pdf.bodySize * 2)
        }
    }

    private fun addHeader() {
        pdf.addTitle(
            title = when (dokument.årsakInnsending) {
                AarsakInnsending.NY -> "Inntektsmelding for sykepenger"
                AarsakInnsending.ENDRING -> "Inntektsmelding for sykepenger - endring"
            },
            x = 0,
            y = y
        )
        // pdf.addImage("logo.svg", 500, y)
        moveCursorBy(pdf.titleSize * 2)
    }

    private fun addAnsatt() {
        addSection("Den ansatte")
        val topY = y
        addLabel("Navn", dokument.fulltNavn, linefeed = false, splitLines = true)
        val afterY = y
        moveCursorTo(topY)
        addLabel("Personnummer", dokument.identitetsnummer, KOLONNE_TO)
        moveCursorTo(afterY)
        moveCursorBy(pdf.bodySize * 2)
    }

    private fun addArbeidsgiver() {
        addSection("Arbeidsgiveren")
        val topY = y
        addLabel("Virksomhetsnavn", dokument.virksomhetNavn, linefeed = false, splitLines = true)
        val afterY = y
        moveCursorTo(topY)
        addLabel("Organisasjonsnummer for underenhet", dokument.orgnrUnderenhet, KOLONNE_TO)
        moveCursorTo(afterY)
        moveCursorBy(pdf.bodySize * 2)
        val newY = y
        addLabel(
            label = "Innsender",
            text = dokument.innsenderNavn,
            linefeed = false,
            splitLines = true
        )
        moveCursorTo(newY)
        addLabel("Telefonnummer", dokument.telefonnummer?.formaterTelefonnummer(), KOLONNE_TO)
    }

    private fun addFraværsperiode() {
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

    private fun addPerioder(x: Int, perioder: List<Periode>) {
        perioder.forEach {
            addLabel("Fra", it.fom.toNorsk(), x, linefeed = false)
            addLabel("Til", it.tom.toNorsk(), x + 200)
        }
    }

    private fun addInntekt() {
        addSection("Beregnet månedslønn")
        addLabel("Registrert inntekt (per ${dokument.bestemmendeFraværsdag.toNorsk()})", dokument.beregnetInntekt.toNorsk() + " kr/måned")
        val endringsårsak = dokument.inntekt?.endringÅrsak
        when (endringsårsak) {
            null -> return // trenger ikke sende inn årsak...
            is Permisjon -> addPermisjon(endringsårsak)
            is Ferie -> addFerie(endringsårsak)
            is Ferietrekk -> addFerietrekk()
            is Permittering -> addPermittering(endringsårsak)
            is Tariffendring -> addTariffendring(endringsårsak)
            is VarigLonnsendring -> addVarigLonnsendring(endringsårsak)
            is NyStilling -> addNyStilling(endringsårsak)
            is NyStillingsprosent -> addNyStillingsprosent(endringsårsak)
            is Bonus -> addBonus(endringsårsak)
            is Sykefravaer -> addSykefravaer(endringsårsak)
            is Nyansatt -> addNyAnsatt()
            is Feilregistrert -> addFeilregistrert()
        }
    }

    private fun addInntektEndringPerioder(endringsårsak: String, perioder: List<Periode>) {
        addLabel(FORKLARING_ENDRING, endringsårsak, linefeed = false)
        addPerioder(KOLONNE_TO, perioder)
    }

    private fun addPermisjon(permisjon: Permisjon) {
        addInntektEndringPerioder("Permisjon", permisjon.liste)
    }

    private fun addFerie(ferie: Ferie) {
        addInntektEndringPerioder("Ferie", ferie.liste)
    }

    private fun addPermittering(permittering: Permittering) {
        addInntektEndringPerioder("Permittering", permittering.liste)
    }
    private fun addSykefravaer(endringsårsak: Sykefravaer) {
        addInntektEndringPerioder("Sykefravær", endringsårsak.liste)
    }
    private fun addNyAnsatt() {
        addLabel(FORKLARING_ENDRING, "Nyansatt")
    }
    private fun addFeilregistrert() {
        addLabel(FORKLARING_ENDRING, "Mangelfull eller uriktig rapportering til A-ordningen")
    }
    private fun addTariffendring(tariffendring: Tariffendring) {
        addLabel(FORKLARING_ENDRING, "Tariffendring")
        addLabel("Gjelder fra", tariffendring.gjelderFra.toNorsk(), linefeed = false)
        addLabel("Ble kjent", tariffendring.bleKjent.toNorsk(), KOLONNE_TO)
    }

    private fun addVarigLonnsendring(varigLonnsendring: VarigLonnsendring) {
        addLabel(FORKLARING_ENDRING, "Varig lønnsendring")
        addLabel("Gjelder fra", varigLonnsendring.gjelderFra.toNorsk())
    }

    private fun addNyStilling(nyStilling: NyStilling) {
        addLabel(FORKLARING_ENDRING, "Ny stilling", linefeed = false)
        addLabel("Gjelder fra", nyStilling.gjelderFra.toNorsk(), KOLONNE_TO)
    }

    private fun addNyStillingsprosent(nyStillingsprosent: NyStillingsprosent) {
        addLabel(FORKLARING_ENDRING, "Ny stillingsprosent", linefeed = false)
        addLabel("Gjelder fra", nyStillingsprosent.gjelderFra.toNorsk(), KOLONNE_TO)
    }

    private fun addBonus(bonus: Bonus) {
        val årligBonus = 0.toBigDecimal() // TODO Må bruke bonus fra datamodell
        val datoBonus = LocalDate.now() // TODO Må bruke dato fra datamodell
        addLabel(FORKLARING_ENDRING, "Bonus")
        // addLabel("Estimert årlig bonus", årligBonus.toNorsk())
        // addLabel("Dato siste bonus", datoBonus.toNorsk())
    }
    private fun addFerietrekk() {
        addLabel(FORKLARING_ENDRING, "Ferietrekk/Utbetaling av feriepenger")
    }

    private fun addRefusjon() {
        addSection("Refusjon")

        val lønnArbeidsgiverperioden = dokument.fullLønnIArbeidsgiverPerioden
        if (lønnArbeidsgiverperioden != null) {
            addLabel("Betaler arbeidsgiver full lønn til arbeidstaker i arbeidsgiverperioden?", lønnArbeidsgiverperioden.utbetalerFullLønn.toNorsk())
            if (lønnArbeidsgiverperioden.utbetalerFullLønn) {
                // Ja
            } else {
                // Nei - to ekstra spørsmål
                addLabel("Begrunnelse", lønnArbeidsgiverperioden.begrunnelse?.tekst() ?: "-")
                addLabel("Utbetalt under arbeidsgiverperiode", (lønnArbeidsgiverperioden.utbetalt?.toNorsk() ?: "-") + " kr")
            }
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
            addLabel("Endringer i refusjon i perioden", (endringer.isNotEmpty()).toNorsk())
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

    private fun addNaturalytelser() {
        addSection("Bortfall av naturalytelser")
        val antallNaturalytelser = dokument.naturalytelser?.size ?: 0
        if (antallNaturalytelser == 0) {
            addLabel("Nei")
        } else {
            addLabel("Naturalytelser", x = NATURALYTELSE_1, linefeed = false)
            addLabel("Dato naturalytelse bortfaller", x = NATURALYTELSE_2, linefeed = false)
            addLabel("Verdi naturalytelse - kr/måned", x = NATURALYTELSE_3)
            dokument.naturalytelser?.forEach {
                addText(it.naturalytelse.name, NATURALYTELSE_1, linefeed = false)
                addText(it.dato.toNorsk(), NATURALYTELSE_2, linefeed = false)
                addText(it.beløp.toNorsk(), NATURALYTELSE_3)
            }
            moveCursorBy(pdf.bodySize * 2)
        }
    }

    private fun addTidspunkt() {
        pdf.addItalics("Innsendt: ${dokument.tidspunkt.toNorsk()}", KOLONNE_EN, y)
        moveCursorBy(pdf.bodySize)
    }
}
