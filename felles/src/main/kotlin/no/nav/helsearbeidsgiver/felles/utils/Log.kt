package no.nav.helsearbeidsgiver.felles.utils

import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import java.util.UUID

object Log {
    fun <T : Any> klasse(value: T) = "class" to value::class.simpleName.orEmpty()

    fun event(value: EventName) = "event" to value.name

    fun priNotis(value: Pri.NotisType) = "pri_notis" to value.name

    fun behov(value: BehovType) = "behov" to value.name

    fun transaksjonId(value: UUID) = "transaksjon_id" to value.toString()

    fun forespoerselId(value: UUID) = "forespoersel_id" to value.toString()

    fun selvbestemtId(value: UUID) = "selvbestemt_id" to value.toString()

    fun sakId(value: String) = "sak_id" to value

    fun oppgaveId(value: String) = "oppgave_id" to value

    fun apiRoute(value: String) = "api_route" to value

    fun ukjentType(value: String) = "ukjent_type" to value
}
