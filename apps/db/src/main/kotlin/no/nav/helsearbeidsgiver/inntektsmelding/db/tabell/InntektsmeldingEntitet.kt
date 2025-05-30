package no.nav.helsearbeidsgiver.inntektsmelding.db.tabell

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.domene.EksternInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.db.domene.InntektsmeldingGammeltFormat
import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.json.jsonb

object InntektsmeldingEntitet : Table("inntektsmelding") {
    val inntektsmeldingId = uuid("inntektsmelding_id").nullable()
    val forespoerselId = uuid("forespoersel_id")
    val skjema =
        jsonb<SkjemaInntektsmelding>(
            name = "skjema",
            jsonConfig = jsonConfig,
            kSerializer = SkjemaInntektsmelding.serializer(),
        ).nullable()
    val inntektsmelding =
        jsonb<Inntektsmelding>(
            name = "inntektsmelding",
            jsonConfig = jsonConfig,
            kSerializer = Inntektsmelding.serializer(),
        ).nullable()
    val dokument =
        jsonb<InntektsmeldingGammeltFormat>(
            name = "dokument",
            jsonConfig = jsonConfig,
            kSerializer = InntektsmeldingGammeltFormat.serializer(),
        ).nullable()
    val eksternInntektsmelding =
        jsonb<EksternInntektsmelding>(
            name = "ekstern_inntektsmelding",
            jsonConfig = jsonConfig,
            kSerializer = EksternInntektsmelding.serializer(),
        ).nullable()
    val avsenderNavn = text("avsender_navn").nullable()
    val journalpostId = text("journalpost_id").nullable()
    val innsendt = datetime("innsendt")
    val prosessert = datetime("prosessert").nullable()
}
