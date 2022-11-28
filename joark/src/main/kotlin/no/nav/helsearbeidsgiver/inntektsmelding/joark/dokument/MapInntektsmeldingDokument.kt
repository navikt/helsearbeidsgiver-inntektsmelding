@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.asLocalDate
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

fun JsonNode.hasText(name: String): Boolean {
    return this.has(name) && !this.get(name).asText().isEmpty()
}

fun parseFullLønnIPerioden(data: JsonNode): FullLønnIArbeidsgiverPerioden {
    return FullLønnIArbeidsgiverPerioden(
        data.get("utbetalerFullLønn").asBoolean(),
        if (data.hasText("begrunnelse")) BegrunnelseIngenEllerRedusertUtbetalingKode.valueOf(data.get("begrunnelse").asText()) else null,
        if (data.hasText("utbetalt")) data.get("utbetalt").asDouble() else null
    )
}

fun parseRefusjon(data: JsonNode): Refusjon {
    return Refusjon(
        if (data.hasText("refusjonPrMnd")) data.get("refusjonPrMnd").asDouble() else null,
        if (data.hasText("refusjonOpphører")) data.get("refusjonOpphører").asLocalDate() else null
    )
}

fun parseÅrsakBeregnetInntektEndringKodeliste(data: JsonNode): ÅrsakBeregnetInntektEndringKodeliste? {
    if (data.hasNonNull("endringÅrsak")) {
        val text = data.get("endringÅrsak").asText()
        if (text.isEmpty()) {
            return null
        }
        return ÅrsakBeregnetInntektEndringKodeliste.valueOf(text)
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
        parseÅrsakBeregnetInntektEndringKodeliste(data.get("inntekt")),
        parseFullLønnIPerioden(data.get("fullLønnIArbeidsgiverPerioden")),
        parseRefusjon(data.get("refusjon")),
        parseNaturalytelser(data.get("naturalytelser")),
        LocalDateTime.now(),
        ÅrsakInnsending.valueOf(data.get("årsakInnsending").asText()),
        data.get("identitetsnummer").asText()
    )
}

class UgyldigFormatException(ex: Exception) : Exception(ex)
