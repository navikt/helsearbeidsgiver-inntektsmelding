package no.nav.hag.simba.utils.kontrakt.domene.inntektsmelding.test

import no.nav.hag.simba.utils.kontrakt.domene.inntektsmelding.EksternInntektsmelding
import no.nav.helsearbeidsgiver.utils.test.date.kl
import no.nav.helsearbeidsgiver.utils.test.date.oktober

fun mockEksternInntektsmelding(): EksternInntektsmelding =
    EksternInntektsmelding(
        avsenderSystemNavn = "Trygge Trygves Trygdesystem",
        avsenderSystemVersjon = "T1000",
        arkivreferanse = "Arkiv nr. 49",
        tidspunkt = 12.oktober.kl(14, 0, 12, 0),
    )
