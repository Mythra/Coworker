package io.kungfury.coworker

import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.runBlocking

fun <T> AwaitValue(value: Deferred<T>) = runBlocking<T> {
    value.await()
}
