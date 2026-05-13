package no.nav.hag.simba.kontrakt.domene.ansettelsesforhold

import kotlinx.serialization.builtins.MapSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr

val ansettelsesforholdSerializer =
    MapSerializer(
        Orgnr.serializer(),
        Ansettelsesforhold.serializer().list(),
    )
