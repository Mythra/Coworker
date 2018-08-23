package io.kungfury.coworker.dbs

import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec

class MarginaliaSpec : FunSpec({
    test("can properly append marginalia") {
        val text = Marginalia.AddMarginalia("MarginaliaSpec_append_marg", "a")
        text shouldBe "a /* Class: io.kungfury.coworker.dbs.MarginaliaSpec$1$1, Function: invoke, Line: 8 */;"
    }

    test("appends a semicolon") {
        val text = Marginalia.AddMarginalia("MarginaliaSpec_append_semi", "a")
        text.endsWith(";") shouldBe true
    }
})
