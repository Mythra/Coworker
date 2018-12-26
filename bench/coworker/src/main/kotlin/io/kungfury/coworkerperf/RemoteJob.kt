package io.kungfury.coworkerperf

import io.kungfury.coworker.BackgroundKotlinWork
import io.kungfury.coworker.WorkGarbage
import io.kungfury.coworker.dbs.ConnectionManager

import okhttp3.OkHttpClient
import okhttp3.Request

import java.time.Instant

/**
 * Represents a job that calls a remote resource, and wait a bit for it to be completed. 5 minutes.
 */
class RemoteJob(
    private val connectionManager: ConnectionManager,
    garbageHeap: WorkGarbage,
    id: Long,
    stage: Int,
    strand: String,
    priority: Int
) : BackgroundKotlinWork(garbageHeap, id, stage, strand, priority) {
    override fun serializeState(): String = ""

    override suspend fun Work(state: String) {
        when (Stage) {
            1 -> {
                val client = OkHttpClient()
                val req = Request.Builder().url("https://google.com").build()
                val resp = client.newCall(req).execute()
                System.out.println(resp.body()!!.string())
                System.out.println(Instant.now().epochSecond)
                yieldNextStage(connectionManager, Instant.now().plusSeconds(60L))
            }
            2 -> {
                System.out.println("Fake remote job completed")
                finishWork()
            }
            else -> {
                System.err.println("Unknown stage: $Stage")
                failWork(connectionManager, "RemoteJob", "Invalid Stage: $Stage")
            }
        }
    }
}