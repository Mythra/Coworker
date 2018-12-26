package io.kungfury.coworker

import io.kungfury.coworker.dbs.ConnectionManager
import io.kungfury.coworker.dbs.ConnectionType
import io.kungfury.coworker.dbs.Marginalia
import io.kungfury.coworker.dbs.Marginalia.AddMarginalia
import io.kungfury.coworker.utils.NetworkUtils

import kotlinx.coroutines.Job

import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant

import kotlin.coroutines.CoroutineContext

/**
 * Describes a "Piece of Work" that needs to be worked in the background potentially past the life of one app server.
 */
interface DelayedKotlinWork {
    val Id: Long
    val Stage: Int
    val Priority: Int
    val Strand: String
    val GarbageHeap: WorkGarbage

    /**
     * Get the time this piece of work started working at for computing long running workers.
     */
    fun getStartTime(): Instant

    /**
     * Serializes this works current state.
     */
    fun serializeState(): String

    /**
     * Run part of a delayed piece of work.
     *
     * @param state
     *  The state of this work to run.
     */
    fun WorkPart(state: String, coroutineContext: CoroutineContext): Job

    /**
     * Yields the piece of work to higher priority work, moving to an arbitrary stage.
     *
     * @param connectionManager
     *  The postgres connection manager.
     * @param stage
     *  The stage to yield to.
     * @param runAt
     *  An optional time to enforce the next stage doesn't run before.
     *
     * <p>
     *  yieldStage should be called when "part" of a piece of work is done, but not all of it.
     * </p>
     */
    suspend fun yieldStage(connectionManager: ConnectionManager, stage: Int, instant: Instant = Instant.now()) {
        val stateToSerialize = this.serializeState()

        when (connectionManager.CONNECTION_TYPE) {
            ConnectionType.POSTGRES -> {
                connectionManager.executeTransaction ({ connection: Connection ->
                    val statement = connection.prepareStatement(AddMarginalia(
                        "DelayedKotlinWork_yieldNext",
                        "UPDATE public.delayed_work SET run_at = ?, stage = ?, state = ?, locked_by = NULL WHERE id = ?"
                    ))
                    statement.setTimestamp(1, Timestamp.from(instant))
                    statement.setInt(2, stage)
                    statement.setString(3, stateToSerialize)
                    statement.setLong(4, this.Id)
                    statement.execute()

                    connection.createStatement().execute(Marginalia.AddMarginalia(
                        "DelayedKotlinWork_yieldStage_notify",
                        String.format("NOTIFY workers, '%s'", "$Id;$Priority;${instant.epochSecond};$stage;${this.Strand}"))
                    )

                    true
                }, true)
            }
        }
    }

    /**
     * Yields the piece of work to higher priority work, progressing the stage.
     *
     * @param connectionManager
     *  The postgres connection manager.
     * @param runAt
     *  An optional time to enforce the next stage doesn't run before.
     *
     * <p>
     *  yieldNextStage should be called when "part" of a piece of work is done, but not all of it.
     *  This increments the stage by one so when the next part is run it's "progressed".
     * </p>
     */
    suspend fun yieldNextStage(connectionManager: ConnectionManager, instant: Instant = Instant.now()) =
        yieldStage(connectionManager, this.Stage + 1, instant = instant)

    /**
     * Yields the piece of work to higher priority work, not progressing the stage of this work.
     *
     * @param connectionManager
     *  The postgres connection manager.
     * @param runAt
     *  An optional time to enforce the next stage doesn't run before.
     *
     * <p>
     *     yieldCurrentStage should called when "part" of a piece of work is not done, but you're still
     *     in a place to yield to higher priority work. E.g. part of a piece of work has failed and you want to retry
     *     but yield to any higher priority work before you retry.
     * </p>
     */
    suspend fun yieldCurrentStage(connectionManager: ConnectionManager, instant: Instant = Instant.now()) =
        yieldStage(connectionManager, this.Stage, instant = instant)

    /**
     * Finish the piece of work, mark it as completed.
     */
    suspend fun finishWork() = GarbageHeap.AddJobToCleanupHeap(Id)

    /**
     * Mark a piece of work as failed.
     *
     * @param connectionManager
     *  The postgres connection manager.
     * @param workName
     *  The unique name of this piece of work.
     * @param failedMsg
     *  The failure message.
     */
    suspend fun failWork(connectionManager: ConnectionManager, workName: String, failedMsg: String) {
        when (connectionManager.CONNECTION_TYPE) {
            ConnectionType.POSTGRES -> {
                connectionManager.executeTransaction({ connection: Connection ->
                    val statement = connection.prepareStatement(AddMarginalia(
                        "DelayedKotlinWork_failWorkDelete",
                        "DELETE FROM public.delayed_work WHERE id = ?"
                    ))
                    statement.setLong(1, this.Id)
                    statement.execute()

                    val createFailed = connection.prepareStatement(AddMarginalia(
                        "DelayedKotlinWork_failWorkCreate",
                        "INSERT INTO public.failed_work (id, failed_at, stage, work_unique_name, failed_msg, state, run_by) VALUES ( ?, current_timestamp, ?, ?, ?, ?, ? )"
                    ))
                    createFailed.setLong(1, this.Id)
                    createFailed.setInt(2, this.Stage)
                    createFailed.setString(3, workName)
                    createFailed.setString(4, failedMsg)
                    createFailed.setString(5, this.serializeState())
                    createFailed.setString(6, NetworkUtils.getLocalHostLANAddress().hostAddress)
                    createFailed.execute()

                    true
                }, true)
            }
        }
    }
}
