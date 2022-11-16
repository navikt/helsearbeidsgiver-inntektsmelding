package no.nav.helsearbeidsgiver.inntektsmelding.joark

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.asLocalDate
import java.time.LocalDate

fun mapInntektsmelding(jsonNode: JsonNode): Inntektsmelding {
    try {
        return parseInntektsmelding(jsonNode)
    } catch (ex: Exception) {
        ex.printStackTrace()
        throw UgyldigFormatException(ex)
    }
}

fun parseBehandlingsdager(data: JsonNode): List<LocalDate> {
    return data.map { it.asLocalDate() }
}

fun parseEgenmeldinger(data: JsonNode): List<Egenmelding> {
    return data.map { Egenmelding(it.get("fom").asLocalDate(), it.get("tom").asLocalDate()) }
}

fun parseNaturalytelser(data: JsonNode): List<Naturalytelse> {
    return data.map { Naturalytelse(it.get("naturalytelseKode").asText(), it.get("dato").asLocalDate(), it.get("beløp").asDouble()) }
}

fun parseInntektsmelding(data: JsonNode): Inntektsmelding {
    return Inntektsmelding(
        data.get("orgnrUnderenhet").asText(),
        data.get("identitetsnummer").asText(),
        data.get("behandlingsdagerFom").asLocalDate(),
        data.get("behandlingsdagerTom").asLocalDate(),
        parseBehandlingsdager(data.get("behandlingsdager")),
        parseEgenmeldinger(data.get("egenmeldinger")),
        data.get("bruttoInntekt").asDouble(),
        data.get("bruttoBekreftet").asBoolean(),
        data.get("utbetalerFull").asBoolean(),
        data.get("begrunnelseRedusert").asText(),
        data.get("utbetalerHeleEllerDeler").asBoolean(),
        data.get("refusjonPrMnd").asDouble(),
        data.get("opphørerKravet").asBoolean(),
        data.get("opphørSisteDag").asLocalDate(),
        parseNaturalytelser(data.get("naturalytelser")),
        data.get("bekreftOpplysninger").asBoolean()
    )
}

class UgyldigFormatException(ex: Exception) : Exception(ex)
