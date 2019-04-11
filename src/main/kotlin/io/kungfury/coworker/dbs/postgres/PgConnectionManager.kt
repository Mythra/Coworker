package io.kungfury.coworker.dbs.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

import io.kungfury.coworker.dbs.ConnectionManager
import io.kungfury.coworker.dbs.ConnectionType
import io.kungfury.coworker.dbs.Marginalia
import io.kungfury.coworker.dbs.TextSafety

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

import org.postgresql.PGConnection

import org.slf4j.LoggerFactory

import java.io.IOException
import java.sql.Connection
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeoutException
import java.util.function.Function

/**
 * Manages a connection pool to postgres.
 */
class PgConnectionManager : ConnectionManager {
    private val LOGGER = LoggerFactory.getLogger(PgConnectionManager::class.java)
    private val connectionPool: HikariDataSource
    private val timeoutLong: Long?
    private var metricRegistry: MeterRegistry = Metrics.globalRegistry
    private val queryTimer: Timer

    /**
     * A generic java compatible constructor for PgConnectionManager.
     *
     * @param configureSource
     *  A function that takes in a "HikariConfig" object, configures it, and returns a configured HikariConfig.
     * @param timeout
     *  An optional timeout in ms. Defaults to 300000L.
     * @param metricRegistry
     *  The metric registry to use.
     */
    constructor(configureSource: Function<HikariConfig, HikariConfig>, timeout: Long?, metricRegistry: MeterRegistry? = null) {
        val finalizedConfig = configureSource.apply(HikariConfig())
        connectionPool = HikariDataSource(finalizedConfig)
        timeoutLong = timeout
        if (metricRegistry != null) {
            this.metricRegistry = metricRegistry
        }
        queryTimer = this.metricRegistry.timer("coworker.pg.query_time", Tags.empty())
    }

    /**
     * A PgConnectionManager constructor built specifically for kotlin.
     *
     * @param configureSource
     *  A block that takes a HikariConfig, configures it, and returns a configured HikariConfig.
     */
    constructor(configureSource: (toConfigure: HikariConfig) -> HikariConfig) {
        val finalizedConfig = configureSource(HikariConfig())
        connectionPool = HikariDataSource(finalizedConfig)
        timeoutLong = null
        queryTimer = this.metricRegistry.timer("coworker.pg.query_time", Tags.empty())
    }

    /**
     * A PgConnectionManager constructor built specifically for kotlin with timeout.
     *
     * @param configureSource
     *  A block that takes a HikariConfig, configures it, and returns a configured HikariConfig.
     * @param timeout
     *  An optional timeout value.
     */
    constructor(configureSource: (toConfigure: HikariConfig) -> HikariConfig, timeout: Long?) {
        val finalizedConfig = configureSource(HikariConfig())
        connectionPool = HikariDataSource(finalizedConfig)
        timeoutLong = timeout
        queryTimer = this.metricRegistry.timer("coworker.pg.query_time", Tags.empty())
    }

    /**
     * A PgConnectionManager constructor built specifically for kotlin with timeout.
     *
     * @param configureSource
     *  A block that takes a HikariConfig, configures it, and returns a configured HikariConfig.
     * @param timeout
     *  An optional timeout value.
     * @param meterRegistry
     *  The metric registry to use
     */
    constructor(configureSource: (toConfigure: HikariConfig) -> HikariConfig, timeout: Long?, meterRegistry: MeterRegistry?) {
        val finalizedConfig = configureSource(HikariConfig())
        connectionPool = HikariDataSource(finalizedConfig)
        timeoutLong = timeout
        if (meterRegistry != null) {
            this.metricRegistry = meterRegistry
        }
        queryTimer = this.metricRegistry.timer("coworker.pg.query_time", Tags.empty())
    }

    override val TIMEOUT_MS
        get() = if (timeoutLong == null) { 300000L } else { timeoutLong }
    override val CONNECTION_TYPE: ConnectionType = ConnectionType.POSTGRES

    @Throws(TimeoutCancellationException::class, IOException::class, IllegalStateException::class)
    override fun <T> executeTransaction(query: Function<Connection, T>, commitOnExit: Boolean): T {
        return queryTimer.recordCallable {
            runBlocking {
                withTimeout(TIMEOUT_MS) {
                    CoroutineName("Coworker - ExecuteTransactionJava")

                    var result: T? = null
                    var error: Exception? = null
                    var conn: Connection? = null

                    try {
                        conn = connectionPool.connection
                        conn.autoCommit = false
                        result = query.apply(conn)
                        if (commitOnExit) {
                            conn.commit()
                        }
                    } catch (e: Exception) {
                        if (conn != null) {
                            conn.rollback()
                            error = e
                        }
                    } finally {
                        if (conn != null) {
                            conn.close()
                        }
                    }

                    if (!isActive) {
                        throw TimeoutException("Failed to complete in time!")
                    }

                    if (error != null) {
                        throw error
                    }

                    if (result == null) {
                        throw IllegalStateException("Result was null at the end of executeTransactionJava")
                    } else {
                        result
                    }
                }
            }
        }
    }

    @Throws(TimeoutCancellationException::class, IOException::class, IllegalStateException::class)
    override suspend fun <T> executeTransaction(query: suspend (Connection) -> T, commitOnExit: Boolean): T {
        return withTimeout(TIMEOUT_MS) {
            CoroutineName("Coworker - ExecuteTransaction")
            val timeStart = Instant.now()

            var result: T? = null
            var error: Exception? = null
            var conn: Connection? = null

            try {
                conn = connectionPool.connection
                conn.autoCommit = false
                result = query(conn)
                if (commitOnExit) {
                    conn.commit()
                }
            } catch (e: Exception) {
                if (conn != null) {
                    conn.rollback()
                }
                error = e
            } finally {
                if (conn != null) {
                    conn.close()
                }
            }
            val timeEnd = Instant.now()
            queryTimer.record(Duration.between(timeStart, timeEnd))

            if (!isActive) {
                throw TimeoutException("Failed to complete in time!")
            }

            if (error != null) {
                throw error
            }

            if (result == null) {
                throw IllegalStateException("Result was null at the end of executeTransaction")
            } else {
                result
            }
        }
    }

    @UseExperimental(ExperimentalCoroutinesApi::class)
    override fun listenToChannel(channel: String, failureLimit: Short): ReceiveChannel<String> {
        val failureGauge = this.metricRegistry.gauge("coworker.listen.failure_gauge", Tags.of(Tag.of("channel", channel)), 0)

        return GlobalScope.produce {
            CoroutineName("PostgresListen( $channel )")

            // So all in all this is subpar.
            //
            // Why is this subpar do you ask? Well because postgres keeps LISTEN/NOTIFY setup to one connection.
            // Meaning there's no way to efficiently only use one connection from the thread pool whenever we need it.
            // We always have to bogart one connection for LISTEN/NOTIFY.
            //
            // It's also subpar because there's no real way to know if we've got a transient failure, or if we need
            // to reconnect to the db. Even if we were to enumerate all exception possibilities, differing architectures
            // may mean something is "transient" while in someone elses architecture it isn't.
            //
            // So this method attempts to be "acceptable to failures". Everytime we get an error we'll increase a
            // counter. If the counter hits failureLimit, we determine "this is a bad connection",
            // and attempt to get a new one. If we fail to get a new connection the channel closes and you should reopen.
            var raw_conn: Connection? = null
            var conn: PGConnection?
            var counter = 0

            try {
                raw_conn = connectionPool.connection
                conn = raw_conn.unwrap(PGConnection::class.java)

                // Ensure we only have a valid channel name, and not some rando sql injection.
                val newChannel = TextSafety.EnforceStringPurity(channel)

                if (newChannel != channel) {
                    throw IllegalStateException("invalid channel name!")
                }
                val statement = raw_conn.createStatement()
                statement.execute(Marginalia.AddMarginalia("PgConnectionManager_listenToChannel", "LISTEN $newChannel"))
                statement.close()

                while (true) {
                    if (counter >= failureLimit) {
                        raw_conn = connectionPool.connection
                        conn = raw_conn.unwrap(PGConnection::class.java)
                    }

                    // Assert we have valid values.
                    raw_conn!!
                    conn!!

                    try {
                        // Execute a query to ensure we can connect to the backend.
                        val stmt = raw_conn.createStatement()
                        stmt.execute(Marginalia.AddMarginalia("PgConnectionManager_selectOnlineCheck", "SELECT 1"))
                        stmt.close()

                        conn.notifications?.forEach { pgNotification ->
                            if (pgNotification.name.equals(channel)) {
                                send(pgNotification.parameter)
                            }
                        }
                        if (counter > 0) {
                            failureGauge?.dec()
                            counter--
                        }
                    } catch (err: Exception) {
                        LOGGER.error("Failed to check for notification on connection!\n${err.message}\n" +
                            "  ${err.stackTrace.joinToString("\n  ")}")
                        failureGauge?.inc()
                        counter++
                    }
                    Thread.sleep(500)
                }
            } catch (err: Exception) {
                LOGGER.error("Failed to setup listen channel, or listen channel has thrown unknown exception.\n" +
                    "${err.message}\n  ${err.stackTrace.joinToString("\n  ")}")
            } finally {
                raw_conn?.close()
            }

            this.channel.close()
        }
    }
}
