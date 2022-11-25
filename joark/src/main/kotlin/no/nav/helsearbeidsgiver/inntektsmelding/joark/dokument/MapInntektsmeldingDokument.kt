package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.asLocalDate
import java.time.LocalDate
import java.time.LocalDateTime

fun mapInntektsmelding(jsonNode: JsonNode, fulltNavn: String, arbeidsgiver: String): InntektsmeldingDokument {
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

fun parseEgenmeldinger(data: JsonNode): List<Periode> {
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

fun parseFullLønnIPerioden(data: JsonNode): FullLønnIArbeidsgiverPerioden {
    return FullLønnIArbeidsgiverPerioden(
        data.get("utbetalerFullLønn").asBoolean(),
        BegrunnelseIngenEllerRedusertUtbetalingKode.valueOf(data.get("begrunnelse").asText())
    )
}

fun parseHeleEllerDeler(data: JsonNode): Refusjon {
    return Refusjon(
        data.get("refusjonPrMnd").asDouble(),
        data.get("refusjonOpphører").asLocalDate()
    )
}

fun parseInntektsmelding(data: JsonNode, fulltNavn: String, arbeidsgiver: String): InntektsmeldingDokument {
    return InntektsmeldingDokument(
        data.get("orgnrUnderenhet").asText(),
        data.get("identitetsnummer").asText(),
        fulltNavn,
        arbeidsgiver,
        parseBehandlingsdager(data.get("behandlingsdager")),
        parseEgenmeldinger(data.get("egenmeldingsperioder")),
        LocalDate.now(), // TODO Bestemmende fraværsdag
        emptyList(), // Todo Fraværsperioder
        emptyList(), // TODO Arbeidsgiverperioder
        data.get("beregnetInntekt").asDouble(),
        ÅrsakBeregnetInntektEndringKodeliste.valueOf(data.get("beregnetInntektEndringÅrsak").asText()),
        parseFullLønnIPerioden(data.get("fullLønnIArbeidsgiverPerioden")),
        parseHeleEllerDeler(data.get("refusjon")),
        parseNaturalytelser(data.get("naturalytelser")),
        LocalDateTime.now(), // TODO Innsendingstidspunkt
        ÅrsakInnsending.valueOf(data.get("årsakInnsending").asText()),
        data.get("identitetsnummerInnsender").asText()
    )
}

class UgyldigFormatException(ex: Exception) : Exception(ex)
