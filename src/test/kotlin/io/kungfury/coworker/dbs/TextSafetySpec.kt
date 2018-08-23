package io.kungfury.coworker.dbs

import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec

/**
 * Specs for the TextSafety class
 */
class TextSafetySpec : FunSpec({
    test("filters out common SQLi chars") {
        val text = "a/*'*/;'@!#$%^&**(()"
        TextSafety.EnforceStringPurity(text) shouldBe "a"
    }

    test("can optionally allow dashes") {
        val text = "a/*'*/;'@!#$%^&**(()-"
        TextSafety.EnforceStringPurity(text) shouldBe "a"
        TextSafety.EnforceStringPurity(text, allowDashes = true) shouldBe "a-"
    }
})