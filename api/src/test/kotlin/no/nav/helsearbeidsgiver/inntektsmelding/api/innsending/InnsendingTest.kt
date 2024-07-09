package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.kotest.core.spec.style.FunSpec
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.NyStilling
import no.nav.helsearbeidsgiver.felles.test.mock.gyldigInnsendingRequest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.resource.readResource
import java.time.LocalDate

class InnsendingTest :
    FunSpec({
        test("skal serialisere InntektEndringÅrsak") {
            val inntekt =
                Inntekt(
                    bekreftet = false,
                    beregnetInntekt = 300.0,
                    endringÅrsak = NyStilling(LocalDate.now()),
                    manueltKorrigert = false,
                )
            println(inntekt.toJson(Inntekt.serializer()))
        }

        test("skal lese innsendingrequest") {
            val request = "innsendingrequest.json".readResource().fromJson(Innsending.serializer())
            request.validate()
        }

        test("skal kunne konvertere til json") {
            println(gyldigInnsendingRequest.toJson(Innsending.serializer()))
        }
    })
