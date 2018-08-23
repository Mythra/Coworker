package io.kungfury.coworker.utils

import io.kotlintest.shouldNotBe
import io.kotlintest.specs.FunSpec

class NetworkUtilsSpec : FunSpec({
    test("it can determine a localhost addr") {
        val addr = NetworkUtils.getLocalHostLANAddress()
        addr shouldNotBe null
    }
})
