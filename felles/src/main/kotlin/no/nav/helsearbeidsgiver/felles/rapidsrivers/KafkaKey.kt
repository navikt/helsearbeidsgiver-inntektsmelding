package no.nav.helsearbeidsgiver.felles.rapidsrivers

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

@Serializable
@JvmInline
value class KafkaKey private constructor(
    internal val key: String,
) {
    constructor(forespoerselId: UUID) : this(forespoerselId.toString())
    constructor(sykmeldtFnr: Fnr) : this(sykmeldtFnr.verdi)
}
