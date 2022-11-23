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

fun parseEgenmeldinger(data: JsonNode): List<EgenmeldingPeriode> {
    return data.map { EgenmeldingPeriode(it.get("fom").asLocalDate(), it.get("tom").asLocalDate()) }
}

fun parseNaturalytelser(data: JsonNode): List<Naturalytelse> {
    return data.map { Naturalytelse(it.get("naturalytelseKode").asText(), it.get("dato").asLocalDate(), it.get("beløp").asDouble()) }
}

fun parseBruttoInntekt(data: JsonNode): Bruttoinntekt {
    return Bruttoinntekt(
        data.get("bekreftet").asBoolean(),
        data.get("bruttoInntekt").asDouble(),
        data.get("endringaarsak").asText(),
        data.get("manueltKorrigert").asBoolean()
    )
}

fun parseFullLønnIPerioden(data: JsonNode): FullLønnIArbeidsgiverPerioden {
    return FullLønnIArbeidsgiverPerioden(
        data.get("utbetalerFullLønn").asBoolean(),
        BegrunnelseIngenEllerRedusertUtbetalingKode.valueOf(data.get("begrunnelse").asText())
    )
}

fun parseHeleEllerDeler(data: JsonNode): HeleEllerdeler {
    return HeleEllerdeler(
        data.get("utbetalerHeleEllerDeler").asBoolean(),
        data.get("refusjonPrMnd").asDouble(),
        data.get("opphørSisteDag").asLocalDate()
    )
}

fun parseInntektsmelding(data: JsonNode): Inntektsmelding {
    return Inntektsmelding(
        data.get("orgnrUnderenhet").asText(),
        data.get("identitetsnummer").asText(),
        parseBehandlingsdager(data.get("behandlingsdager")),
        parseEgenmeldinger(data.get("egenmeldingsperioder")),
        parseBruttoInntekt(data.get("bruttoInntekt")),
        parseFullLønnIPerioden(data.get("fullLønnIArbeidsgiverPerioden")),
        parseHeleEllerDeler(data.get("heleEllerdeler")),
        parseNaturalytelser(data.get("naturalytelser")),
        data.get("bekreftOpplysninger").asBoolean()
    )
}

class UgyldigFormatException(ex: Exception) : Exception(ex)
