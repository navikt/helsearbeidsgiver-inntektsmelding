package no.nav.hag.simba.utils.felles.utils

import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import java.util.UUID

object Log {
    fun <T : Any> klasse(value: T) = "hag_class" to value::class.simpleName.orEmpty()

    fun event(value: EventName) = "hag_event_name" to value.name

    fun priNotis(value: String) = "hag_pri_notis" to value

    fun behov(value: BehovType) = "hag_behov" to value.name

    fun kontekstId(value: UUID) = "hag_kontekst_id" to value.toString()

    fun inntektsmeldingId(value: UUID) = "hag_inntektsmelding_id" to value.toString()

    fun forespoerselId(value: UUID) = "hag_forespoersel_id" to value.toString()

    fun selvbestemtId(value: UUID) = "hag_selvbestemt_id" to value.toString()

    fun apiRoute(value: String) = "hag_api_route" to value
}
