package no.nav.helsearbeidsgiver.felles.utils

import kotlinx.serialization.Serializable

interface Retriable {

    /*
    Kunne kanskje bare vært en statisk metode, heller enn interface.
    Dersom retryId er null, er det en vanlig pakke og skal behandles
    Dersom retryId er satt, må man sjekke om den matcher ens egen listenerId,
    bare da skal pakken behandles.
     */
    fun erRetryOgMatcherLytteren(myListenerID: RetryID, retryId: RetryID?): Boolean {
        return retryId == null || myListenerID == retryId
    }
}

@Serializable
enum class RetryID {
    JOURNALFOER
}
