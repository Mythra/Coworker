package io.kungfury.coworker.consul

import com.jsoniter.spi.JsonException

import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.shouldThrowAny
import io.kotlintest.specs.FunSpec
import io.kungfury.coworker.AwaitValue

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

import java.io.IOException
import java.util.Optional
import java.util.concurrent.TimeUnit

class ConsulServiceCheckerSpec : FunSpec({
    val dispatcher: Dispatcher = object : Dispatcher() {
        @Throws(InterruptedException::class)
        override fun dispatch(request: RecordedRequest): MockResponse {

            when (request.path) {
                "/v1/catalog/service/foobar" -> return MockResponse().setResponseCode(200).setBody("[{\"ID\": \"40e4a748-2192-161a-0510-9bf59fe950b5\",\"Node\": \"foobar\",\"Address\": \"192.168.10.10\",\"Datacenter\": \"dc1\",\"TaggedAddresses\": {\"lan\": \"192.168.10.10\",\"wan\": \"10.0.10.10\"},\"NodeMeta\": {\"somekey\": \"somevalue\"},\"CreateIndex\": 51,\"ModifyIndex\": 51,\"ServiceAddress\": \"172.17.0.3\",\"ServiceEnableTagOverride\": false,\"ServiceID\": \"32a2a47f7992:nodea:5000\",\"ServiceName\": \"foobar\",\"ServicePort\": 5000,\"ServiceMeta\": {\"foobar_meta_value\": \"baz\"},\"ServiceTags\": [\"tacos\"]}]")
                "/v1/catalog/service/foobar-minimal" -> return MockResponse().setResponseCode(200).setBody("[{\"ID\": \"40e4a748-2192-161a-0510-9bf59fe950b5\",\"Address\": \"192.168.10.10\",\"ServiceAddress\": \"172.17.0.3\",\"ServiceName\": \"foobar\"}]")
                "/v1/catalog/service/foobar-obj" -> return MockResponse().setResponseCode(200).setBody("{\"ID\": \"SUP\"}")
                "/v1/catalog/service/foobar-empty" -> return MockResponse().setResponseCode(200).setBody("[]")
                "/v1/catalog/service/foobar-nil" -> return MockResponse().setResponseCode(200).setBody("")
                "/v1/catalog/service/foobar-sleep" -> {
                    return MockResponse().setResponseCode(200).setBody("[{\"ID\": \"40e4a748-2192-161a-0510-9bf59fe950b5\",\"Node\": \"foobar\",\"Address\": \"192.168.10.10\",\"Datacenter\": \"dc1\",\"TaggedAddresses\": {\"lan\": \"192.168.10.10\",\"wan\": \"10.0.10.10\"},\"NodeMeta\": {\"somekey\": \"somevalue\"},\"CreateIndex\": 51,\"ModifyIndex\": 51,\"ServiceAddress\": \"172.17.0.3\",\"ServiceEnableTagOverride\": false,\"ServiceID\": \"32a2a47f7992:nodea:5000\",\"ServiceName\": \"foobar\",\"ServicePort\": 5000,\"ServiceMeta\": {\"foobar_meta_value\": \"baz\"},\"ServiceTags\": [\"tacos\"]}]").setBodyDelay(5000L, TimeUnit.MILLISECONDS)
                }
                else -> return MockResponse().setResponseCode(404)
            }
        }
    }
    val server = MockWebServer()
    server.dispatcher = dispatcher
    server.start()

    test("getNewOfflineNodes can parse a full service") {
        val url = server.url("/v1").toUri().toASCIIString()
        val serviceChecker = ConsulServiceChecker(url, "foobar", Optional.empty(), null)

        val removed_one = AwaitValue(serviceChecker.getNewOfflineNodes())
        val removed_two = AwaitValue(serviceChecker.getNewOfflineNodes())

        removed_one.size shouldBe 0
        removed_two.size shouldBe 0

        val nodes = serviceChecker.getHostList()
        nodes.size shouldBe 1
        nodes[0] shouldBe "172.17.0.3"
    }

    test("getNewOfflineNodes can parse a minimal service") {
        val url = server.url("/v1").toUri().toASCIIString()
        val serviceChecker = ConsulServiceChecker(url, "foobar-minimal", Optional.empty(), null)

        val removed_one = AwaitValue(serviceChecker.getNewOfflineNodes())
        val removed_two = AwaitValue(serviceChecker.getNewOfflineNodes())

        removed_one.size shouldBe 0
        removed_two.size shouldBe 0

        val nodes = serviceChecker.getHostList()
        nodes.size shouldBe 1
        nodes[0] shouldBe "172.17.0.3"
    }

    test("getNewOfflineNodes throws when an unexpected code is enouctered") {
        val url = server.url("/v1").toUri().toASCIIString()
        val serviceChecker = ConsulServiceChecker(url, "foobarbaz", Optional.empty(), null)

        shouldThrow<IOException> {
            AwaitValue(serviceChecker.getNewOfflineNodes())
        }
    }

    test("getNewOfflineNodes throws an exception on not being able to parse an array") {
        val url = server.url("/v1").toUri().toASCIIString()
        val serviceChecker = ConsulServiceChecker(url, "foobar-obj", Optional.empty(), null)
        val svcChecker = ConsulServiceChecker(url, "foobar-nil", Optional.empty(), null)

        shouldThrow<ArrayIndexOutOfBoundsException> {
            AwaitValue(svcChecker.getNewOfflineNodes())
        }
        shouldThrow<JsonException> {
            AwaitValue(serviceChecker.getNewOfflineNodes())
        }
    }

    test("getNewOfflineNodes can timeout") {
        val url = server.url("/v1").toUri().toASCIIString()
        val serviceChecker = ConsulServiceChecker(url, "foobar-sleep", Optional.empty(), 1L)

        shouldThrowAny {
            AwaitValue(serviceChecker.getNewOfflineNodes())
        }
    }

    test("it can find the difference between two lists") {
        val url = server.url("/v1").toUri().toASCIIString()
        val serviceChecker = ConsulServiceChecker(url, "foobar-obj", Optional.empty(), null)

        // Test two lists with just added/remove content.
        val listOne = listOf(
            "a",
            "b"
        )
        val listTwo = listOf(
            "a",
            "b",
            "c"
        )

        val diff = serviceChecker.DiffIterables(listOne, listTwo)
        val diff_two = serviceChecker.DiffIterables(listTwo, listOne)

        diff.first.size shouldBe 0
        diff.second.size shouldBe 1
        diff.second[0] shouldBe "c"

        diff_two.second.size shouldBe 0
        diff_two.first.size shouldBe 1
        diff_two.first[0] shouldBe "c"

        // Test add and remove
        val listThree = listOf(
            "a",
            "b"
        )
        val listFour = listOf(
            "c",
            "d"
        )

        val diff_three = serviceChecker.DiffIterables(listThree, listFour)

        diff_three.first.size shouldBe 2
        diff_three.first[0] shouldBe "a"
        diff_three.first[1] shouldBe "b"

        diff_three.second.size shouldBe 2
        diff_three.second[0] shouldBe "c"
        diff_three.second[1] shouldBe "d"
    }
})
