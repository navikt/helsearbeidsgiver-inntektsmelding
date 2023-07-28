package no.nav.helsearbeidsgiver.felles.utils

import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import java.util.UUID

object Log {
    fun <T : Any> klasse(value: T) = "class" to value.simpleName()

    fun event(value: EventName) = "event" to value.name

    fun behov(value: BehovType) = "behov" to value.name

    fun transaksjonId(value: UUID) = "transaksjon_id" to value.toString()

    fun clientId(value: UUID) = "client_id" to value.toString()

    fun forespoerselId(value: UUID) = "forespoersel_id" to value.toString()
}
