package no.nav.helsearbeidsgiver.inntektsmelding.db.tabell

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.json.jsonb

object SelvbestemtInntektsmeldingEntitet : Table("selvbestemt_inntektsmelding") {
    val inntektsmeldingId = javaUUID("inntektsmelding_id")
    val selvbestemtId = javaUUID("selvbestemt_id")
    val inntektsmelding =
        jsonb<Inntektsmelding>(
            name = "inntektsmelding",
            jsonConfig = jsonConfig,
            kSerializer = Inntektsmelding.serializer(),
        )
    val journalpostId = text("journalpost_id").nullable()
    val opprettet = datetime("opprettet")
    val prosessert = datetime("prosessert").nullable()
}
