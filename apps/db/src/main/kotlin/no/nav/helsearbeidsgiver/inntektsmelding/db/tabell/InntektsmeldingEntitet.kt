package no.nav.helsearbeidsgiver.inntektsmelding.db.tabell

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.json.jsonb

// 'skjema' og 'avsender_navn' kan bli non-nullable etter at alle eksterne inntektsmeldinger er fjernet fra databasen.
object InntektsmeldingEntitet : Table("inntektsmelding") {
    // non-null for rader opprettet etter 27.03.25 kl. 10:00
    val inntektsmeldingId = javaUUID("inntektsmelding_id").nullable()
    val forespoerselId = javaUUID("forespoersel_id")
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
    val avsenderNavn = text("avsender_navn").nullable()
    val journalpostId = text("journalpost_id").nullable()
    val innsendt = datetime("innsendt")
    val prosessert = datetime("prosessert").nullable()
}
