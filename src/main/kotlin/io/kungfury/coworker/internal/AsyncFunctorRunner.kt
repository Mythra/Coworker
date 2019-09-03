package io.kungfury.coworker.internal

import com.jsoniter.JsonIterator

import io.kungfury.coworker.BackgroundKotlinWork
import io.kungfury.coworker.WorkGarbage
import io.kungfury.coworker.dbs.ConnectionManager
import io.kungfury.coworker.internal.states.HandleAsyncFunctorState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import java.io.ObjectInputStream
import java.util.function.Function

/**
 * Run an asynchronous function.
 */
class AsyncFunctorRunner(
    private val connectionManager: ConnectionManager,
    garbageHeap: WorkGarbage,
    id: Long,
    stage: Int,
    strand: String,
    priority: Int
) : BackgroundKotlinWork(garbageHeap, id, stage, strand, priority) {
    private var internalState = ""

    override fun serializeState(): String = internalState

    inline fun <reified T : Any> ByteArray.deserialize(): T =
        ObjectInputStream(inputStream()).readObject() as T

    override suspend fun Work(state: String) {
        internalState = state
        try {
            val parsed: HandleAsyncFunctorState = withContext(Dispatchers.IO) {
                JsonIterator.parse(internalState).read(HandleAsyncFunctorState::class.java)
            }
            val serializedFunctor = parsed.methodState.serializedClosure

            if (!parsed.isJava) {
                val func: (Array<com.jsoniter.any.Any>) -> Unit = serializedFunctor.deserialize()
                func(parsed.args)
            } else {
                val func: Function<Array<com.jsoniter.any.Any>, Void> = serializedFunctor.deserialize()
                // If we don't do this we get: `Error: func.apply(parsed.args) must not be null` when a function
                // returns null
                @Suppress("UNUSED_VARIABLE") var any: Any? = func.apply(parsed.args)
            }

            finishWork()
        } catch (err: Exception) {
            failWork(
                connectionManager,
                "io.kungfury.coworker.internal.AsyncFunctorRunner",
                "Error: ${err.message}\n  ${err.stackTrace.joinToString("\n  ")}"
            )
        }
    }
}