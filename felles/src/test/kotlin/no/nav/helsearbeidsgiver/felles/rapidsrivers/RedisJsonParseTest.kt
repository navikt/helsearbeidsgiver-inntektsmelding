package no.nav.helsearbeidsgiver.felles.rapidsrivers

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.parse
import no.nav.helsearbeidsgiver.utils.json.toJsonStr

class RedisJsonParseTest : FunSpec({
    test("fnr (vanlig string) som starter på 0 parses korrekt") {
        val fnr = "01010154321"
        val expected: JsonElement = JsonPrimitive(fnr)

        val actual = parse(fnr)

        actual shouldBe expected
        (actual as JsonPrimitive).isString.shouldBeTrue()
    }

    test("fnr (json-string) som starter på 0 parses korrekt") {
        val expected: JsonElement = JsonPrimitive("01010154321")

        val actual = parse("\"01010154321\"")

        actual shouldBe expected
        (actual as JsonPrimitive).isString.shouldBeTrue()
    }

    test("tall (json-string) parses til string") {
        val fnr = 2468.toJsonStr(Int.serializer())
        val expected: JsonElement = JsonPrimitive(fnr)

        val actual = parse(fnr)

        actual shouldBe expected
        (actual as JsonPrimitive).isString.shouldBeTrue()
    }
})
