package no.nav.helsearbeidsgiver.felles.rapidsrivers.model

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.utils.json.fromJson

class ModelUtils {

    companion object {
        fun toFailOrNull(json: Map<IKey, JsonElement>): Fail? =
            json[Key.FAIL]
                ?.runCatching {
                    fromJson(Fail.serializer())
                }
                ?.getOrNull()
    }
}
