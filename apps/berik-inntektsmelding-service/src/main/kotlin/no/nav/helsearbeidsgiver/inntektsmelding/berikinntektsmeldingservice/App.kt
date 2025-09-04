package no.nav.helsearbeidsgiver.inntektsmelding.berikinntektsmeldingservice

import no.nav.hag.simba.utils.rr.Publisher
import no.nav.hag.simba.utils.rr.river.ObjectRiver
import no.nav.hag.simba.utils.rr.service.ServiceRiverStateless

fun main() {
    ObjectRiver.connectToRapid {
        createBerikInntektsmeldingService(it)
    }
}

fun createBerikInntektsmeldingService(publisher: Publisher): List<ServiceRiverStateless> =
    listOf(
        ServiceRiverStateless(
            BerikInntektsmeldingService(publisher),
        ),
    )
