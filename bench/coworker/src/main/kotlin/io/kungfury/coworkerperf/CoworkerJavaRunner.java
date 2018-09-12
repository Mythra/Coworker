package io.kungfury.coworkerperf;

import com.zaxxer.hikari.HikariConfig;

import io.kungfury.coworker.CoworkerManager;
import io.kungfury.coworker.StaticCoworkerConfigurationInput;
import io.kungfury.coworker.dbs.ConnectionManager;
import io.kungfury.coworker.dbs.postgres.PgConnectionManager;

import java.time.Duration;
import java.util.HashMap;
import java.util.function.Function;

public class CoworkerJavaRunner {
    public static void main(String[] args) {
        int threads = Integer.parseInt(System.getenv("THREADS"));
        System.out.println("Starting coworker with: [ " + threads + " ] threads.");
        ConnectionManager cm = new PgConnectionManager((Function<HikariConfig, HikariConfig>) (hikariConfig -> {
            hikariConfig.setJdbcUrl(System.getenv("JDBC_URL"));
            return hikariConfig;
        }), null);
        CoworkerManager manager = new CoworkerManager(cm, threads, null,
            new StaticCoworkerConfigurationInput(Duration.parse("PT5M"), new HashMap<>()));
        manager.Start();
    }
}
