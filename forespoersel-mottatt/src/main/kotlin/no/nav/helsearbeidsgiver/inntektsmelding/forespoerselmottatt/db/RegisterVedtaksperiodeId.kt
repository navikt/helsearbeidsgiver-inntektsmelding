package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt.db

import no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt.db.RegisterVedtaksperiodeId.long
import no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt.db.RegisterVedtaksperiodeId.uuid
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import java.util.UUID

object RegisterVedtaksperiodeId : Table(Meta.TABLE_NAME) {
    val id = Meta.ID.asLong().autoIncrement(
        idSeqName = Meta.SEQ_ID
    )
    val forespoerselId = Meta.FORESPOERSEL_ID.asUuid()
    val vedtaksperiodeId = Meta.VEDTAKSPERIODE_ID.asUuid()
}

private object Meta {
    const val TABLE_NAME = "register_vedtaksperiode_id"

    const val SEQ_ID = "register_vedtaksperiode_id_id_seq"

    const val ID = "id"
    const val FORESPOERSEL_ID = "forespoersel_id"
    const val VEDTAKSPERIODE_ID = "vedtaksperiode_id"
}

private fun String.asLong(): Column<Long> =
    long(this)

private fun String.asUuid(): Column<UUID> =
    uuid(this)
