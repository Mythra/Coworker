# Setup in Code With Coworker #

Getting started with Coworker is hopefully a rather simple process. Unfortunately there
are some pre-requisites to standing up Coworker. You must be running:

* A PostgreSQL database, running at least version 9.5.
* Java 8 or higher.

If you've met these requirements than congratulations you have everything you need
to run a minimal version of coworker. However, it should be noted if you do not
also run [Consul](https://www.consul.io/) then you will ***Not have node failover healing***.
As we depend on consul in order to tell us if a node has perished or not.

You'll also need to run [Migrations](migrations.md) which have been documented in this repo.

## Starting up Coworker ##

Although coworker can techincally be started up from anywhere, we recommend distributing
a unique jar, that shades in your normal code for Coworker. This allows you to distribute
just whats necessary for coworker which will hopefully leave more room for Coworker to just
work on the jobs themselves. However if you wish to distribute as part of the same jar and just
spin it up based on a command line parameter or in the background you're 100% free to do that.

### Creating a ConnectionManager ###

To start up Coworker you first need to create an instance of a PgConnectionManager.
PgConnectionManager is the base class for how Coworker pools it's connection to postgres.
PgConnectionManager takes in a function the function will receive a `HikariDataSource`
instance (since Coworker uses [HikariCP](https://github.com/brettwooldridge/HikariCP) under the hood
to pool connections to the database), and the function should return a configured HikariDataSource
this allows you to get the configuration for the data source however you wish. The examples below
will for example fetch a full JDBC_URL from the environment, and pass in `null` for the timeout
to use the default timeout:

***Kotlin:***

```kotlin
import io.kungfury.coworker.dbs.postgres.PgConnectionManager

fun getConnectionManager(): PgConnectionManager {
  return PgConnectionManager({ toConfigure ->
    toConfigure.jdbcUrl = System.getenv("JDBC_URL")
    toConfigure
  }, null)
}
```

***Java:***

```java
import com.zaxxer.hikari.HikariConfig;
import io.kungfury.coworker.dbs.postgres.PgConnectionManager;

public class Utils {
  static PgConnectionManager getConnectionManager() {
    return new PgConnectionManager((Function<HikariConfig, HikariConfig>) (hikariConfig -> {
      hikariConfig.setJdbcUrl(System.getenv("JDBC_URL"));
      return hikariConfig;
    }), null);
  }
}
```

### Creating a Configuration Object ###

Next you'll want to create a configuration object. Coworker provides a default StaticConfigurationObject
which is what we recommend to start. However it is possible to create a configuration object that gets updated
values over time. Which may be useful for your application if you want to make configuration changes without
stopping/starting your workers.

In this example we'll create a static configuration object. The two pieces of configuration we'll
need to provide are as follows:

| Variable Name   | Description                                                                                                                                                       |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| checkWorkEvery  | How often we perform a full scan of the table to check for work we didn't get a notification for. Ideally this should always return 0 results, but things happen. |
| nstrand         | A map of "nstrands". Nstrands allow you to limit jobs based on an ad-hoc tag. View the n-stranding documentation for more info.                                   |
| failureLimit    | How many failures should be allowed while processing a stream of notifications before achieving a new connection.                                                 |
| garbageHeapSize | The max size of DELETEs to buffer (note there is also a duration factor).                                                                                         |
| cleanupDuration | The duration in seconds to cleanup regardless of size.                                                                                                            |

***Kotlin:***

```kotlin
import io.kungfury.coworker.StaticCoworkerConfigurationInput

import java.time.Duration

fun getStaticConfiguration(): StaticCoworkerConfigurationInput {
  return StaticCoworkerConfigurationInput(Duration.parse("PT5M"), HashMap(), 3, 1000, Duration.ofSeconds(30))
}
```

***Java:***

```java
import io.kungfury.coworker.StaticCoworkerConfigurationInput;

import java.time.Duration;
import java.util.HashMap;

public class Utils {
    static StaticCoworkerConfigurationInput getStaticConfiguration() {
        return new StaticCoworkerConfigurationInput(Duration.parse("PT5M"), new HashMap<>(), 3, 1000, Duration.ofSeconds(30));
    }
}
```

### Creating a Coworker Instance ###

Once you've gone ahead and creating a ConnectionManager + a Configuration Object, you're finally
ready to create an Instance of Coworker. Remember this part of the documentation does not cover using
consul for node recovery, so we'll pass in: `null` for our ServiceChecker:

***Kotlin:***

```kotlin
import io.kungfury.coworker.CoworkerManager
import io.kungfury.coworker.StaticCoworkerConfigurationInput
import io.kungfury.coworker.dbs.postgres.PgConnectionManager

fun createCoworkerInstance(config: StaticCoworkerConfigurationInput, cm: PgconnectionManager): CoworkerManager {
    return CoworkerManager(cm, 10, null, config)
}
```

***Java:***

```java
import io.kungfury.coworker.CoworkerManager;
import io.kungfury.coworker.dbs.postgres.PgConnectionManager;
import io.kungfury.coworker.StaticCoworkerConfigurationInput;

public class Utils {
    static CoworkerManager createCoworkerInstance(StaticCoworkerConfigurationInput config, PgConnectionManager cm) {
        return new CoworkerManager(cm, 10, null, config);
    }
}
```

Once you've created your instance of the CoworkerManager all you need to do is call: `start()`
on the instance of the object you created, and that thread will spin up a thread pool starting
to work through it's queue. ***NOTE: This will hijack the thread that calls start, as well as spinning up
it's own thread pool.***
