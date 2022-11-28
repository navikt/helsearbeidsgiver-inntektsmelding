@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asOptionalLocalDate
import java.time.LocalDate
import java.time.LocalDateTime

fun mapInntektsmeldingDokument(jsonNode: JsonNode, fulltNavn: String, arbeidsgiver: String): InntektsmeldingDokument {
    try {
        return parseInntektsmelding(jsonNode, fulltNavn, arbeidsgiver)
    } catch (ex: Exception) {
        ex.printStackTrace()
        throw UgyldigFormatException(ex)
    }
}

fun parseBehandlingsdager(data: JsonNode): List<LocalDate> {
    return data.map { it.asLocalDate() }
}

fun parsePerioder(data: JsonNode): List<Periode> {
    return data.map { Periode(it.get("fom").asLocalDate(), it.get("tom").asLocalDate()) }
}

fun parseNaturalytelser(data: JsonNode): List<Naturalytelse> {
    return data.map {
        Naturalytelse(
            NaturalytelseKode.valueOf(it.get("naturalytelse").asText()),
            it.get("dato").asLocalDate(),
            it.get("beløp").asDouble()
        )
    }
}

fun JsonNode.getOptionalDouble(name: String): Double? {
    if (this.hasNonNull(name)) {
        val node = this.get(name)
        return node.asDouble()
    }
    return null
}

fun parseFullLønnIPerioden(data: JsonNode): FullLønnIArbeidsgiverPerioden {
    return FullLønnIArbeidsgiverPerioden(
        data.get("utbetalerFullLønn").asBoolean(),
        data.getOptionalBegrunnelseIngenEllerRedusertUtbetalingKode("begrunnelse"),
        data.getOptionalDouble("utbetalt")
    )
}

fun JsonNode.getOptionalBegrunnelseIngenEllerRedusertUtbetalingKode(name: String): BegrunnelseIngenEllerRedusertUtbetalingKode? {
    if (this.hasNonNull(name)) {
        return BegrunnelseIngenEllerRedusertUtbetalingKode.valueOf(this.get(name).asText())
    }
    return null
}

fun JsonNode.getOptionalLocalDate(name: String): LocalDate? {
    if (this.hasNonNull(name)) {
        val node = this.get(name)
        return node.asOptionalLocalDate()
    }
    return null
}

fun parseRefusjon(data: JsonNode): Refusjon {
    return Refusjon(
        data.getOptionalDouble("refusjonPrMnd"),
        data.getOptionalLocalDate("refusjonOpphører")
    )
}

fun JsonNode.getOptionalÅrsakBeregnetInntektEndringKodeliste(name: String): ÅrsakBeregnetInntektEndringKodeliste? {
    if (this.hasNonNull(name)) {
        return ÅrsakBeregnetInntektEndringKodeliste.valueOf(this.get(name).asText())
    }
    return null
}

fun parseInntektsmelding(data: JsonNode, fulltNavn: String, arbeidsgiver: String): InntektsmeldingDokument {
    return InntektsmeldingDokument(
        data.get("orgnrUnderenhet").asText(),
        data.get("identitetsnummer").asText(),
        fulltNavn,
        arbeidsgiver,
        parseBehandlingsdager(data.get("behandlingsdager")),
        parsePerioder(data.get("egenmeldingsperioder")),
        data.get("bestemmendeFraværsdag").asLocalDate(),
        parsePerioder(data.get("fraværsperioder")),
        parsePerioder(data.get("arbeidsgiverperioder")),
        data.get("inntekt").get("beregnetInntekt").asDouble(),
        data.get("inntekt").getOptionalÅrsakBeregnetInntektEndringKodeliste("endringÅrsak"),
        parseFullLønnIPerioden(data.get("fullLønnIArbeidsgiverPerioden")),
        parseRefusjon(data.get("refusjon")),
        parseNaturalytelser(data.get("naturalytelser")),
        LocalDateTime.now(),
        ÅrsakInnsending.valueOf(data.get("årsakInnsending").asText()),
        data.get("identitetsnummer").asText()
    )
}

class UgyldigFormatException(ex: Exception) : Exception(ex)
