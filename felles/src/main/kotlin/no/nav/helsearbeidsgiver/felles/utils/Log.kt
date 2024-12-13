package no.nav.helsearbeidsgiver.felles.utils

import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import java.util.UUID

object Log {
    fun <T : Any> klasse(value: T) = "hag_class" to value::class.simpleName.orEmpty()

    fun event(value: EventName) = "hag_event" to value.name

    fun priNotis(value: Pri.NotisType) = "hag_pri_notis" to value.name

    fun behov(value: BehovType) = "hag_behov" to value.name

    fun transaksjonId(value: UUID) = "hag_transaksjon_id" to value.toString()

    fun forespoerselId(value: UUID) = "hag_forespoersel_id" to value.toString()

    fun selvbestemtId(value: UUID) = "hag_selvbestemt_id" to value.toString()

    fun apiRoute(value: String) = "hag_api_route" to value
}
