package no.nav.helsearbeidsgiver.felles.rapidsrivers.composite

import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail

sealed class Transaction {
    data object New : Transaction()
    data object InProgress : Transaction()
    data object Finalize : Transaction()
    data object NotActive : Transaction()
    data class Terminate(
        val fail: Fail
    ) : Transaction()
}
