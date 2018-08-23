package io.kungfury.coworker.dbs.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

import io.kungfury.coworker.dbs.ConnectionManager
import io.kungfury.coworker.dbs.ConnectionType
import io.kungfury.coworker.dbs.Marginalia
import io.kungfury.coworker.dbs.TextSafety

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.TimeoutCancellationException
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.withTimeout

import org.postgresql.PGConnection

import org.slf4j.LoggerFactory

import java.io.IOException
import java.sql.Connection
import java.util.concurrent.TimeUnit
import java.util.function.Function

/**
 * Manages a connection pool to postgres.
 */
class PgConnectionManager : ConnectionManager {
    private val LOGGER = LoggerFactory.getLogger(PgConnectionManager::class.java)
    private val connectionPool: HikariDataSource
    private val timeoutLong: Long?

    constructor(configureSource: Function<HikariConfig, HikariConfig>, timeout: Long?) {
        val finalizedConfig = configureSource.apply(HikariConfig())
        connectionPool = HikariDataSource(finalizedConfig)
        timeoutLong = timeout
    }

    constructor(configureSource: (toConfigure: HikariConfig) -> HikariConfig, timeout: Long?) {
        val finalizedConfig = configureSource(HikariConfig())
        connectionPool = HikariDataSource(finalizedConfig)
        timeoutLong = timeout
    }

    override val TIMEOUT_MS
        get() = if (timeoutLong == null) { 300000L } else { timeoutLong }
    override val CONNECTION_TYPE: ConnectionType = ConnectionType.POSTGRES

    @Throws(TimeoutCancellationException::class, IOException::class, IllegalStateException::class, Exception::class)
    override fun <T> executeTransaction(query: Function<Connection, T>, commitOnExit: Boolean): T {
        return runBlocking {
            withTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS) {
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
                    throw TimeoutCancellationException("Failed to complete in time!")
                }

                if (error != null) {
                    throw error
                }

                if (result == null) {
                    throw IllegalStateException("Result was null at the end of executeTransactionJava")
                }

                result!!
            }
        }
    }

    @Throws(TimeoutCancellationException::class, IOException::class, IllegalStateException::class, Exception::class)
    override suspend fun <T> executeTransaction(query: suspend (Connection) -> T, commitOnExit: Boolean): T {
        return withTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS) {
            CoroutineName("Coworker - ExecuteTransaction")

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

            if (!isActive) {
                throw TimeoutCancellationException("Failed to complete in time!")
            }

            if (error != null) {
                throw error
            }

            if (result == null) {
                throw IllegalStateException("Result was null at the end of executeTransaction")
            }

            result!!
        }
    }

    override fun listenToChannel(channel: String): ReceiveChannel<String> {
        return produce {
            CoroutineName("PostgresListen( $channel )")
            // So all in all this is subpar.
            //
            // Why is this subpar do you ask? Well because postgres keeps LISTEN/NOTIFY setup to one connection.
            // Meaning there's no way to effeciently only use one connection from the thread pool whenever we need it.
            // We always have to bogart one connection for LISTEN/NOTIFY.
            //
            // It's also subpar because there's no real way to know if we've got a transient failure, or if we need
            // to reconnect to the db.
            //
            // So this method attempts to be "acceptable to failures". Everytime we get an error we'll increase a
            // counter. If the counter hits 3, we determine "this is a bad connection", and attempt to get a new one.
            // If we fail to get a new connection the channel closes and you should re open.
            //
            // You may be asking yourself why 3 failures? And the answer is honestly it was totally arbitrary.
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
                    if (counter >= 3) {
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
                            counter--
                        }
                    } catch (err: Exception) {
                        LOGGER.error("Failed to check for notification on connection!\n${err.message}\n" +
                            "  ${err.stackTrace.joinToString("\n  ")}")
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
