package no.nav.helsearbeidsgiver.inntektsmelding.db.tabell

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.domene.EksternInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.db.domene.InntektsmeldingGammeltFormat
import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.json.jsonb

object InntektsmeldingEntitet : Table("inntektsmelding") {
    val id =
        long("id").autoIncrement(
            idSeqName = "inntektsmelding_id_seq",
        )
    val inntektsmeldingId = uuid("inntektsmelding_id").nullable()
    val forespoerselId = varchar(name = "forespoersel_id", length = 40)
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
    val skjema =
        jsonb<SkjemaInntektsmelding>(
            name = "skjema",
            jsonConfig = jsonConfig,
            kSerializer = SkjemaInntektsmelding.serializer(),
        ).nullable()
    val innsendt = datetime("innsendt")
    val avsenderNavn = text("avsender_navn").nullable()
    val journalpostId = varchar("journalpostid", 30).nullable()

    override val primaryKey = PrimaryKey(id, name = "id")
}
