@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.db

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.inntektsmelding.db.FullLønnIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.felles.inntektsmelding.db.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.inntektsmelding.db.Refusjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.Naturalytelse
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.NaturalytelseKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.Periode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.ÅrsakBeregnetInntektEndringKodeliste
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.ÅrsakInnsending
import java.time.LocalDate
import java.time.LocalDateTime

fun mapInntektsmeldingDokument(jsonNode: JsonNode, fulltNavn: String, arbeidsgiver: String): InntektsmeldingDokument {
    try {
        return jsonNode.parseInntektsmelding(fulltNavn, arbeidsgiver)
    } catch (ex: Exception) {
        throw UgyldigFormatException(ex)
    }
}

fun JsonNode.asBehandlingsdager(): List<LocalDate> {
    return this.map { it.asLocalDate() }
}

fun JsonNode.asPerioder(): List<Periode> {
    return this.map { Periode(it.get("fom").asLocalDate(), it.get("tom").asLocalDate()) }
}

fun JsonNode.asNaturalytelser(): List<Naturalytelse> {
    return this.map {
        Naturalytelse(
            NaturalytelseKode.valueOf(it.get("naturalytelse").asText()),
            it.get("dato").asLocalDate(),
            it.get("beløp").asDouble()
        )
    }
}

fun JsonNode.getFullLønnIPerioden(): FullLønnIArbeidsgiverPerioden {
    return FullLønnIArbeidsgiverPerioden(
        this.get("utbetalerFullLønn").asBoolean(),
        this.getOrNull("begrunnelse")?.asBegrunnelseIngenEllerRedusertUtbetalingKode(),
        this.getOrNull("utbetalt")?.asDouble()
    )
}

fun JsonNode.asBegrunnelseIngenEllerRedusertUtbetalingKode(): BegrunnelseIngenEllerRedusertUtbetalingKode {
    return BegrunnelseIngenEllerRedusertUtbetalingKode.valueOf(this.asText())
}

fun JsonNode.asRefusjon(): Refusjon {
    return Refusjon(
        this.getOrNull("refusjonPrMnd")?.asDouble(),
        this.getOrNull("refusjonOpphører")?.asLocalDate()
    )
}

fun JsonNode.asÅrsakBeregnetInntektEndringKodeliste(): ÅrsakBeregnetInntektEndringKodeliste {
    return ÅrsakBeregnetInntektEndringKodeliste.valueOf(this.asText())
}

fun JsonNode.getOrNull(name: String): JsonNode? {
    if (this.hasNonNull(name)) {
        return this.get(name)
    }
    return null
}

fun JsonNode.asÅrsakInnsending(): ÅrsakInnsending {
    return ÅrsakInnsending.valueOf(this.asText())
}

fun JsonNode.parseInntektsmelding(fulltNavn: String, arbeidsgiver: String): InntektsmeldingDokument {
    return InntektsmeldingDokument(
        this.get(Key.ORGNRUNDERENHET.str).asText(),
        this.get("identitetsnummer").asText(),
        fulltNavn,
        arbeidsgiver,
        this.get("behandlingsdager").asBehandlingsdager(),
        this.get("egenmeldingsperioder").asPerioder(),
        this.get("bestemmendeFraværsdag").asLocalDate(),
        this.get("fraværsperioder").asPerioder(),
        this.get("arbeidsgiverperioder").asPerioder(),
        this.get("inntekt").get("beregnetInntekt").asDouble(),
        this.get("inntekt").getOrNull("endringÅrsak")?.asÅrsakBeregnetInntektEndringKodeliste(),
        this.get("fullLønnIArbeidsgiverPerioden").getFullLønnIPerioden(),
        this.get("refusjon").asRefusjon(),
        this.get("naturalytelser").asNaturalytelser(),
        LocalDateTime.now(),
        this.get("årsakInnsending").asÅrsakInnsending(),
        this.get("identitetsnummer").asText()
    )
}

class UgyldigFormatException(ex: Exception) : Exception(ex)
