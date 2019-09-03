package io.kungfury.coworker.consul

import com.jsoniter.JsonIterator
import com.jsoniter.spi.JsonException

import kotlinx.coroutines.*

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response

import org.slf4j.LoggerFactory

import java.io.IOException
import java.util.Optional
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implements a service checker for consul.
 */
class ServiceChecker(consulUri: String, service: String, consulToken: Optional<String>, timeout: Long?) {
    private val LOGGER = LoggerFactory.getLogger(ServiceChecker::class.java)

    private val consulService = service
    private val consulHost: String = "$consulUri/catalog/service/$consulService"
    private val authToken = consulToken
    private val httpClient = OkHttpClient
        .Builder()
        .build()
    // Default timeout is 5 minutes.
    val consulTimeoutMs = timeout ?: 300000L

    private var hostList = ArrayList<String>()

    /**
     * Grab the host list of the Service Checker.
     */
    fun getHostList(): ArrayList<String> = hostList

    /**
     * Get the name of the service
     */
    fun getServiceName(): String = consulService

    /**
     * Get a list of the new offline nodes.
     */
    fun getNewOfflineNodes(): Deferred<List<String>> {
        return GlobalScope.async {
            withTimeout(consulTimeoutMs) {
                CoroutineName("getNewOfflineNodes - $consulService")

                val reqBuilder = Request.Builder().url(consulHost).get()
                if (authToken.isPresent) {
                    reqBuilder.addHeader("X-Consul-Token", authToken.get())
                }
                val request = reqBuilder.build()
                val resp = MakeAsyncRequest(httpClient, request, this)

                if (!resp.isSuccessful) {
                    throw IOException("Unexpected code from consul ${resp.code}")
                }

                val responses = ArrayList<String>()
                val body = resp.body ?: throw IOException("Expected body back from consul!")

                if (!isActive) {
                    throw TimeoutException("Timeout out waiting for request to succeed.")
                }
                val byteStream = body.byteStream().readBytes()
                if (!isActive) {
                    throw TimeoutException("Timeout out waiting for request to succeed.")
                }

                val parser = JsonIterator.parse(byteStream)

                withContext(Dispatchers.IO) {
                    while (parser.readArray()) {
                        if (!isActive) {
                            throw TimeoutException("Timeout out waiting for request to succeed.")
                        }
                        try {
                            val parsed = parser.read(ConsulServiceResponse::class.java)
                            responses.add(parsed.ServiceAddress)
                        } catch (exc: JsonException) {
                            LOGGER.error("Ran into $exc attempting to parse json object from consul")
                        }
                    }
                }

                if (!isActive) {
                    throw TimeoutException("Timeout out waiting for request to succeed.")
                }

                val removedHosts = DiffIterables(hostList, responses).first
                hostList = responses

                removedHosts
            }
        }
    }

    /**
     * Makes an HTTP Call, returning either the response, or throwing an exception if error occurs.
     *
     * @param httpClient
     *  The HTTP Client to make the request through.
     * @param request
     *  The HTTP Request to make.
     */
    suspend fun MakeAsyncRequest(httpClient: OkHttpClient, request: Request, scope: CoroutineScope): Response {
        val hasError = AtomicBoolean(false)
        val hasSucceeded = AtomicBoolean(false)
        // This should never manifest, but if it does respond with "408"
        // which stands for the client not being able to prepare the request.
        // because that's about as close as we can get.
        //
        // We can't use Response? because smart casting would stop working
        // due to closure mutability. I hear there's a kotlin bug for this,
        // but it isn't finished at the time of writing this.
        var resp: Response = Response
            .Builder()
            .code(408)
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .message("CoroutineHttpRequest failed to manifest request.")
            .build()
        var error: Exception? = null
        val call = httpClient.newCall(request)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                error = e
                hasError.set(true)
            }

            override fun onResponse(call: Call, response: Response) {
                resp = response
                hasSucceeded.set(true)
            }
        })

        withContext(Dispatchers.Unconfined) {
            while (httpClient.dispatcher.runningCalls().contains(call)) {
                if (!scope.isActive) {
                    call.cancel()
                    throw TimeoutException("Timed out waiting for request to succeed.")
                }
            }
        }

        if (hasError.get()) {
            if (error == null) {
                throw IOException("Null error was raised by HTTPClient!")
            } else {
                throw IOException(error)
            }
        }

        if (!hasSucceeded.get()) {
            throw IllegalStateException("Not hasErrord, but not hasSucceeded either in KvClient.")
        }

        return resp
    }

    /**
     * Generates a difference between two iterable values.
     *
     * @param list_one
     *  The first iterable to compare against.
     * @param list_two
     *  The second iterable to compare against.
     *
     * @return
     *  A pair of (removed items, added items). (Where list one is the base).
     */
    fun <T> DiffIterables(list_one: Iterable<T>, list_two: Iterable<T>): Pair<List<T>, List<T>> {
        val RemovedItems = ArrayList<T>()
        val AddedItems = ArrayList<T>()

        for (item: T in list_one) {
            if (!(item in list_two)) {
                RemovedItems.add(item)
            }
        }
        for (item: T in list_two) {
            if (!(item in list_one)) {
                AddedItems.add(item)
            }
        }

        return Pair(RemovedItems, AddedItems)
    }
}
