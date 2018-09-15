# Inserting Work #

Inserting work for Coworker is a bit of a different story than creating a job node that
actually does work. This is because we want to support the use case where you have one
set of nodes inserting delayed work (and doing something like serving a web page), with
another tier of nodes that actually performs the work. As such inserting work only
requires an instance of a: `ConnectionManager`, or a connection to the database itself.

*As a side note, the ConnectionManager for Coworker is built to be rigid, and lock you
into some postgresql best practices around transactions. If you use postgres as your
main data store too, we highly recommend taking a look at using it directly
instead of manually calling out to a pool like HikariCP.*

Besides a connection manager, the InsertWork call also takes the following parameters:

| Variable Name | Description                                                                                      |
|---------------|--------------------------------------------------------------------------------------------------|
| workName      | The full path to the class file to be worked. E.g.: `com.mygroupid.myartifactid.work.MyWorkToDo` |
| workState     | The initial state to pass into the job.                                                          |
| strand        | The strand to queue this job in, defaults to: `default`.                                         |
| runAt         | A specific instant to ensure this job does not run before, defaults to: `Instant.now()`.         |
| priority      | The priority of this job, defaults to: `100`.                                                    |

An example of inserting work using a connection manager, and the minimum values is below:

***Kotlin:***

```kotlin
import io.kungfury.coworker.WorkInserter
import io.kungfury.coworker.dbs.ConnectionManager

fun InsertMyJob(connectionManager: ConnectionManager) {
    WorkInserter.InsertWork(connectionManager, "com.mygroupid.myartifactid.work.MyWorkToDo", "")
}
```

***Java:***

```java
import io.kungfury.coworker.WorkInserter;
import io.kungfury.coworker.dbs.ConnectionManager;

public class Utils {
    static void InsertMyJob(ConnectionManager connectionManager) {
        WorkInserter.INSTANCE.InsertWork(connectionManager, "com.mygroupid.myartifcatid.work.MyWorkToDo", "");
    }
}
```

## Using Handle Asynchronously ##

Coworker also allows you to insert just raw static functors into the job queue, although
these can't yield it can be useful when you just want something to happen in the background.

It should be noted that these are not like Kotlin Coroutines, you will not receive the
value back, the return value of these functions are lost, and as such you shouldn't ever return
 anything but null from these functions. All arguments are passed as the: `Array<Any>` type from
 the [jsoniter](https://jsoniter.com/) library (***NOT FROM KOTLIN STDLIB.***) Example
 Functions are listed below:

 ***Kotlin:***

 ```kotlin
 import com.jsoniter.any.Any

 import io.kungfury.coworker.WorkInserter
 import io.kungfury.coworker.dbs.ConnectionManager

 fun MethodReferenceWithArgs(args: Array<Any>) {
     System.err.println("Method reference: ${args.first().toInt()}")
 }

fun InsertMyJob(connectionManager: ConnectionManager) {
  WorkInserter.HandleAsynchronously(connectionManager, { args: Array<Any> ->
    val value = args.first()
    System.err.println(value.toInt())
  }, arrayOf(10))
  WorkInserter.HandleAsynchronously(connectionManager, ::MethodReferenceWithArgs, arrayOf(10))
}
 ```

 ***Java:***

 ```java
import com.jsoniter.any.Any;

import java.io.Serializable;

public class JavaFunctions implements Serializable {
    public Void nonEmpty(Any[] args) {
        int num = args[0].toInt();
        System.out.println("Num is: " + num);
        return null;
    }

    public static void TestQueueJava(ConnectionManager connManager) {
        WorkInserter.INSTANCE.HandleAsynchronouslyJava(
          connManager,
          (Function<Any[], Void> & Serializable) (obj) -> {
              int num = obj[0].toInt();
              System.err.println("Num is: " + num);
              return null;
          },
          new Any[] {Any.wrap(10)},
          "default",
          Instant.now(),
          100
        );

        JavaFunctions jf = new JavaFunctions();

        WorkInserter.INSTANCE.HandleAsynchronouslyJava(
          connManager,
          (Function <Any[], Void> & Serializable) jf::nonEmpty,
          new Any[] {Any.wrap(10)},
          "default",
          Instant.now(),
          100
        );
    }
}
```
