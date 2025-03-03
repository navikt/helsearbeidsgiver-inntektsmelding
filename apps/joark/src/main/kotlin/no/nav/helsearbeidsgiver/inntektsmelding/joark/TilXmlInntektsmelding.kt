package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Arbeidsgiverperiode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Avsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Bonus
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Feilregistrert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Ferie
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Ferietrekk
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.InntektEndringAarsak
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Naturalytelse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.NyStilling
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.NyStillingsprosent
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Nyansatt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Permisjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Permittering
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.RefusjonEndring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Sykefravaer
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Tariffendring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.VarigLoennsendring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.bestemmendeFravaersdag
import no.seres.xsd.nav.inntektsmelding_m._20181211.Arbeidsforhold
import no.seres.xsd.nav.inntektsmelding_m._20181211.Arbeidsgiver
import no.seres.xsd.nav.inntektsmelding_m._20181211.Avsendersystem
import no.seres.xsd.nav.inntektsmelding_m._20181211.EndringIRefusjon
import no.seres.xsd.nav.inntektsmelding_m._20181211.InntektsmeldingM
import no.seres.xsd.nav.inntektsmelding_m._20181211.Kontaktinformasjon
import no.seres.xsd.nav.inntektsmelding_m._20181211.NaturalytelseDetaljer
import no.seres.xsd.nav.inntektsmelding_m._20181211.Skjemainnhold
import no.seres.xsd.nav.inntektsmelding_m._20181211.SykepengerIArbeidsgiverperioden
import no.seres.xsd.nav.inntektsmelding_m._20181211.Inntekt as InntektXml
import no.seres.xsd.nav.inntektsmelding_m._20181211.Periode as PeriodeXml
import no.seres.xsd.nav.inntektsmelding_m._20181211.Refusjon as RefusjonXml

fun tilXmlInntektsmelding(im: Inntektsmelding): InntektsmeldingM =
    InntektsmeldingM().also {
        it.skjemainnhold = tilSkjemainnhold(im)
    }

private fun tilSkjemainnhold(im: Inntektsmelding): Skjemainnhold =
    Skjemainnhold().also { skjema ->
        skjema.aarsakTilInnsending = im.aarsakInnsending.name
        skjema.arbeidsforhold = im.tilArbeidsforhold()
        skjema.arbeidsgiver = im.avsender.map()
        skjema.arbeidstakerFnr = im.sykmeldt.fnr.verdi
        skjema.avsendersystem =
            Avsendersystem().also {
                it.innsendingstidspunkt = im.mottatt.toLocalDateTime()
                it.systemnavn = "NAV_NO"
                it.systemversjon = "1.0"
            }
        skjema.opphoerAvNaturalytelseListe = im.inntekt?.naturalytelser?.map(Naturalytelse::map)
        skjema.refusjon = im.refusjon.map()
        skjema.sykepengerIArbeidsgiverperioden = im.agp.map()
        skjema.ytelse = "Sykepenger"
    }

private fun Avsender.map(): Arbeidsgiver =
    Arbeidsgiver().also { ag ->
        ag.kontaktinformasjon =
            Kontaktinformasjon().also {
                it.kontaktinformasjonNavn = navn
                it.telefonnummer = tlf
            }
        ag.virksomhetsnummer = orgnr.verdi
    }

private fun Inntektsmelding.tilArbeidsforhold(): Arbeidsforhold =
    Arbeidsforhold().also { af ->
        af.beregnetInntekt =
            InntektXml().also {
                it.aarsakVedEndring = inntekt?.endringAarsaker?.firstOrNull()?.tilTekst()
                it.beloep = inntekt?.beloep?.toBigDecimal()
            }
        af.foersteFravaersdag =
            bestemmendeFravaersdag(
                arbeidsgiverperioder = agp?.perioder.orEmpty(),
                sykefravaersperioder = sykmeldingsperioder,
            )
    }

private fun Refusjon?.map(): RefusjonXml =
    RefusjonXml().also {
        it.refusjonsbeloepPrMnd = this?.beloepPerMaaned?.toBigDecimal()
        it.refusjonsopphoersdato = this?.sluttdato
        it.endringIRefusjonListe = this?.endringer?.map(RefusjonEndring::map)
    }

private fun RefusjonEndring.map(): EndringIRefusjon =
    EndringIRefusjon().also {
        it.endringsdato = startdato
        it.refusjonsbeloepPrMnd = beloep.toBigDecimal()
    }

private fun Naturalytelse.map(): NaturalytelseDetaljer =
    NaturalytelseDetaljer().also {
        it.naturalytelseType = naturalytelse.name
        it.fom = sluttdato
        it.beloepPrMnd = verdiBeloep.toBigDecimal()
    }

private fun Arbeidsgiverperiode?.map(): SykepengerIArbeidsgiverperioden =
    SykepengerIArbeidsgiverperioden().also {
        it.arbeidsgiverperiodeListe = this?.perioder?.ifEmpty { null }?.map(Periode::map)
        it.begrunnelseForReduksjonEllerIkkeUtbetalt = this?.redusertLoennIAgp?.begrunnelse?.name
        it.bruttoUtbetalt = this?.redusertLoennIAgp?.beloep?.toBigDecimal()
    }

private fun Periode.map(): PeriodeXml =
    PeriodeXml().also {
        it.fom = fom
        it.tom = tom
    }

fun InntektEndringAarsak.tilTekst(): String =
    when (this) {
        is Bonus -> "Bonus" // Beløp og dato ikke implementert i frontend
        is Feilregistrert -> "Mangelfull eller uriktig rapportering til A-ordningen"
        is Ferie -> "Ferie: ${ferier.lesbar()}"
        is Ferietrekk -> "Ferietrekk"
        is NyStilling -> "Ny stilling: fra $gjelderFra"
        is NyStillingsprosent -> "Ny stillingsprosent: fra $gjelderFra"
        is Nyansatt -> "Nyansatt"
        is Permisjon -> "Permisjon: ${permisjoner.lesbar()}"
        is Permittering -> "Permittering: ${permitteringer.lesbar()}"
        is Sykefravaer -> "Sykefravær: ${sykefravaer.lesbar()}"
        is Tariffendring -> "Tariffendring: fra $gjelderFra"
        is VarigLoennsendring -> "Varig lønnsendring: fra $gjelderFra"
    }

private fun List<Periode>.lesbar(): String = joinToString { "fra ${it.fom} til ${it.tom}" }
