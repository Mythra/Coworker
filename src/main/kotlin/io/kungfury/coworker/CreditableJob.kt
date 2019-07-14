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
    garbageHeap: WorkGarbage,
    id: Long,
    stage: Int,
    strand: String,
    priority: Int,
    private val workUniqueName: String
) : BackgroundKotlinWork(garbageHeap, id, stage, strand, priority) {
    override fun WorkPart(state: String, coroutineContext: CoroutineContext): Job {
        return CoroutineScope(coroutineContext).launch {
            if (Strand.isEmpty()) {
                return@launch failWork(connectionManager, workUniqueName, "Creditable Jobs need a strand!")
            }

            val jobState = when (connectionManager.CONNECTION_TYPE) {
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
                                "  strand_name = ?"
                        ))
                        statement.setString(1, Strand)

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
                                "  strand_name = ?"
                        ))
                        credit_use.setString(1, Strand)

                        val credit_use_rs = credit_use.executeQuery()
                        if (rs.next()) {
                            in_use = rs.getLong("in_use")
                        }

                        return@executeTransaction CreditableJobState(rolling_avg, total_jobs, in_use)
                    }
                }
            }

            // If a job takes longer than an hour let it run one job.
            if (jobState.in_use + jobState.rolling_avg > 3600 && jobState.in_use != 0L) {
                return@launch yieldCurrentStage(connectionManager, Instant.now().plusSeconds(60))
            }

            when (connectionManager.CONNECTION_TYPE) {
                ConnectionType.POSTGRES -> {
                    connectionManager.executeTransaction { conn ->
                        val ps = conn.prepareStatement(Marginalia.AddMarginalia(
                            "CreditableJob_UpdateInUse",
                            "INSERT INTO" +
                                "  public.delayed_work_credit_use(strand_name, in_use)" +
                                " VALUES" +
                                "  (?, ?)" +
                                " ON CONFLICT(strand_name)" +
                                " DO UPDATE SET " +
                                "  in_use = delayed_work_credit_use.in_use + ?"
                        ))
                        ps.setString(1, Strand)
                        ps.setLong(2, jobState.rolling_avg)
                        ps.setLong(3, jobState.rolling_avg)

                        ps.execute()
                    }
                }
            }

            // TODO: recheck to see if we caused an overflow.

            val time = measureTimeMillis {
                Work(state)
            }

            val newJobCount: Long = jobState.total_jobs + 1

            var newAvg: Long = if (jobState.rolling_avg == 0L) {
                Math.floorDiv(time, 1000)
            } else {
                var new_avg = jobState.rolling_avg - (jobState.rolling_avg / newJobCount)
                new_avg += Math.floorDiv(time, 1000) / newJobCount
                new_avg
            }

            when (connectionManager.CONNECTION_TYPE) {
                ConnectionType.POSTGRES -> {
                    connectionManager.executeTransaction { conn ->
                        val update_in_use_ps = conn.prepareStatement(Marginalia.AddMarginalia(
                            "CreditableJob_UpdateInUse",
                            "UPDATE" +
                                "  public.delayed_work_credit_use" +
                                " SET" +
                                "  in_use = delayed_work_credit_use.in_use - ?"
                        ))
                        update_in_use_ps.setLong(1, jobState.rolling_avg)

                        update_in_use_ps.execute()

                        val update_avg = conn.prepareStatement(Marginalia.AddMarginalia(
                            "CreditableJob_UpdateAvgs",
                            "INSERT INTO" +
                                "  public.delayed_work_credits(strand_name, rolling_average_seconds, total_jobs)" +
                                " VALUES" +
                                "  (?, ?, ?)" +
                                " ON CONFLICT(strand_name)" +
                                " DO UPDATE SET" +
                                "  rolling_average_seconds = ?," +
                                "  total_jobs = ?"
                        ))
                        update_avg.setString(1, Strand)
                        update_avg.setLong(2, newAvg)
                        update_avg.setLong(3, newJobCount)
                        update_avg.setLong(4, newAvg)
                        update_avg.setLong(5, newJobCount)
                        update_avg.execute()
                    }
                }
            }
        }
    }
}
