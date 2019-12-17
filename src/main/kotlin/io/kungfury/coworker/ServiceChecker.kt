package io.kungfury.coworker

import kotlinx.coroutines.Deferred

interface ServiceChecker {
    fun getHostList(): ArrayList<String>

    fun getServiceName(): String

    fun getNewOfflineNodes(): Deferred<List<String>>
}
