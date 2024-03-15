package no.nav.helsearbeidsgiver.felles.utils

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

interface Retriable {

    /*
    Kunne kanskje bare vært en statisk metode, heller enn interface.
    Dersom retryId er null, er det en vanlig pakke og skal behandles
    Dersom retryId er satt, må man sjekke om den matcher ens egen listenerId,
    bare da skal pakken behandles.
     */
    fun erRetryOgMatcherLytteren(myListenerID: RetryID, retryId: RetryID?): Boolean {
        val shouldRun = retryId == null || myListenerID == retryId
        sikkerLogger().info("$myListenerID mottok pakke med id $retryId. Skal kjøre: $shouldRun")
        return shouldRun
    }
}

@Serializable
enum class RetryID {
    JOURNALFOER
}
