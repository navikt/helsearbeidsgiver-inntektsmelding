package no.nav.helsearbeidsgiver.inntektsmelding.berikinntektsmeldingservice

import no.nav.helsearbeidsgiver.felles.rr.Publisher
import no.nav.helsearbeidsgiver.felles.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.rr.service.ServiceRiverStateless

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
