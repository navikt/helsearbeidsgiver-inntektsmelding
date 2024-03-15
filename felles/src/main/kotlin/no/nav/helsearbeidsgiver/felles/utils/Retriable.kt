package no.nav.helsearbeidsgiver.felles.utils

import kotlinx.serialization.Serializable

interface Retriable {

    fun shouldRetry(myListenerID: RetryID, retryId: RetryID?): Boolean {
        return myListenerID == retryId
    }
}

@Serializable
enum class RetryID {
    JOURNALFOER
}
