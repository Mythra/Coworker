package io.kungfury.coworker

import io.kungfury.coworker.dbs.ConnectionManager
import io.kungfury.coworker.dbs.ConnectionType
import io.kungfury.coworker.dbs.Marginalia
import io.kungfury.coworker.internal.states.CreditableJobState

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

import java.time.Instant

import kotlin.coroutines.CoroutineContext
import kotlin.system.measureTimeMillis

/**
 * Creates a "CreditableJob", or a job that can keep track of it's own runs.
 */
@CreditApi
@UseExperimental(CreditApi::class)
abstract class CreditableJob(
    private val connectionManager: ConnectionManager,
    private val workUniqueName: String,
    garbageHeap: WorkGarbage,
    id: Long,
    stage: Int,
    strand: String,
    priority: Int
) : BackgroundKotlinWork(garbageHeap, id, stage, strand, priority) {
    // TODO: Make rolling window.
    private val MAX_SECONDS_WORKABLE: Long = 3600

    private suspend fun GetWorkCredit(): CreditableJobState {
        return when (connectionManager.CONNECTION_TYPE) {
            ConnectionType.POSTGRES -> {
                connectionManager.executeTransaction { conn ->
                    val statement = conn.prepareStatement(Marginalia.AddMarginalia(
                        "CreditableJob_SelectCredits",
                        "SELECT" +
                            "  rolling_average_seconds," +
                            "  total_jobs" +
                            " FROM" +
                            "  public.delayed_work_credits" +
                            " WHERE" +
                            "  strand_name = ?" +
                            " AND" +
                            "  stage = ?" +
                            " AND" +
                            "  job_name = ?"
                    ))
                    statement.setString(1, Strand)
                    statement.setInt(2, Stage)
                    statement.setString(3, workUniqueName)

                    val rs = statement.executeQuery()
                    var rolling_avg: Long = 0
                    var total_jobs: Long = 0

                    if (rs.next()) {
                        rolling_avg = rs.getLong("rolling_average_seconds")
                        total_jobs = rs.getLong("total_jobs")
                    }

                    var in_use: Long = 0
                    val credit_use = conn.prepareStatement(Marginalia.AddMarginalia(
                        "CreditableJob_InUseCount",
                        "SELECT" +
                            "  in_use" +
                            " FROM" +
                            "  public.delayed_work_credit_use" +
                            " WHERE" +
                            "  strand_name = ?" +
                            " AND" +
                            "  stage = ?" +
                            " AND" +
                            "  job_name = ?"
                    ))
                    credit_use.setString(1, Strand)
                    credit_use.setInt(2, Stage)
                    credit_use.setString(3, workUniqueName)

                    val credit_use_rs = credit_use.executeQuery()
                    if (rs.next()) {
                        in_use = rs.getLong("in_use")
                    }

                    return@executeTransaction CreditableJobState(rolling_avg, total_jobs, in_use)
                }
            }
        }
    }

    private suspend fun UpdateCredits(rolling_avg: Long, newAvg: Long, newJobCount: Long) {
        when (connectionManager.CONNECTION_TYPE) {
            ConnectionType.POSTGRES -> {
                connectionManager.executeTransaction { conn ->
                    val update_in_use_ps = conn.prepareStatement(Marginalia.AddMarginalia(
                        "CreditableJob_UpdateInUse",
                        "UPDATE" +
                            "  public.delayed_work_credit_use" +
                            " SET" +
                            "  in_use = GREATEST(delayed_work_credit_use.in_use - ?, 0)" +
                            " WHERE" +
                            "  strand_name = ?" +
                            " AND" +
                            "  stage = ?" +
                            " AND" +
                            "  job_name = ?"
                    ))
                    update_in_use_ps.setLong(1, rolling_avg)
                    update_in_use_ps.setString(2, Strand)
                    update_in_use_ps.setInt(3, Stage)
                    update_in_use_ps.setString(4, workUniqueName)

                    update_in_use_ps.execute()

                    val update_avg = conn.prepareStatement(Marginalia.AddMarginalia(
                        "CreditableJob_UpdateAvgs",
                        "INSERT INTO" +
                            "  public.delayed_work_credits(job_name, strand_name, stage, rolling_average_seconds, total_jobs)" +
                            " VALUES" +
                            "  (?, ?, ?, ?, ?)" +
                            " ON CONFLICT(job_name, strand_name, stage)" +
                            " DO UPDATE SET" +
                            "  rolling_average_seconds = ?," +
                            "  total_jobs = ?"
                    ))
                    update_avg.setString(1, workUniqueName)
                    update_avg.setString(2, Strand)
                    update_avg.setInt(3, Stage)
                    update_avg.setLong(4, newAvg)
                    update_avg.setLong(5, newJobCount)
                    update_avg.setLong(6, newAvg)
                    update_avg.setLong(7, newJobCount)
                    update_avg.execute()
                }
            }
        }
    }

    override fun WorkPart(state: String, coroutineContext: CoroutineContext): Job {
        return CoroutineScope(coroutineContext).launch {
            if (Strand.isEmpty()) {
                return@launch failWork(connectionManager, workUniqueName, "Creditable Jobs need a strand!")
            }

            val jobState = GetWorkCredit()

            // If a job takes longer than an hour let it run one job.
            if (jobState.in_use + jobState.rolling_avg > MAX_SECONDS_WORKABLE && jobState.in_use != 0L) {
                return@launch yieldCurrentStage(connectionManager, Instant.now().plusSeconds(60))
            }

            when (connectionManager.CONNECTION_TYPE) {
                ConnectionType.POSTGRES -> {
                    connectionManager.executeTransaction { conn ->
                        val ps = conn.prepareStatement(Marginalia.AddMarginalia(
                            "CreditableJob_UpdateInUse",
                            "INSERT INTO" +
                                "  public.delayed_work_credit_use(job_name, strand_name, stage, in_use)" +
                                " VALUES" +
                                "  (?, ?, ?, ?)" +
                                " ON CONFLICT(job_name, strand_name, stage)" +
                                " DO UPDATE SET " +
                                "  in_use = delayed_work_credit_use.in_use + ?"
                        ))
                        ps.setString(1, workUniqueName)
                        ps.setString(2, Strand)
                        ps.setInt(3, Stage)
                        ps.setLong(4, jobState.rolling_avg)
                        ps.setLong(5, jobState.rolling_avg)

                        ps.execute()
                    }
                }
            }

            val newState = GetWorkCredit()
            if (newState.in_use > MAX_SECONDS_WORKABLE) {
                UpdateCredits(jobState.rolling_avg, jobState.rolling_avg, jobState.total_jobs)
                return@launch yieldCurrentStage(connectionManager, Instant.now().plusSeconds(60))
            }

            val time = measureTimeMillis { Work(state) }

            val newJobCount: Long = jobState.total_jobs + 1

            val newAvg: Long = if (jobState.rolling_avg == 0L) {
                Math.floorDiv(time, 1000)
            } else {
                var new_avg = jobState.rolling_avg - (jobState.rolling_avg / newJobCount)
                new_avg += Math.floorDiv(time, 1000) / newJobCount
                new_avg
            }

            UpdateCredits(jobState.rolling_avg, newAvg, newJobCount)
        }
    }
}
