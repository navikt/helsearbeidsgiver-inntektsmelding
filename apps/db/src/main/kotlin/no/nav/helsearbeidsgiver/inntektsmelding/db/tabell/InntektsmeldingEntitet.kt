package no.nav.helsearbeidsgiver.inntektsmelding.db.tabell

import no.nav.hag.simba.kontrakt.domene.inntektsmelding.EksternInntektsmelding
import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.json.jsonb
import no.nav.hag.simba.utils.felles.domene.InntektsmeldingIntern as Inntektsmelding
import no.nav.hag.simba.utils.felles.domene.SkjemaInntektsmeldingIntern as SkjemaInntektsmelding

object InntektsmeldingEntitet : Table("inntektsmelding") {
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
