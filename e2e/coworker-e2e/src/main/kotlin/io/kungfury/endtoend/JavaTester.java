package io.kungfury.endtoend;

import com.jsoniter.any.Any;

import io.kungfury.coworker.WorkInserter;
import io.kungfury.coworker.dbs.ConnectionManager;

import java.io.Serializable;
import java.time.Instant;
import java.util.function.Function;

public class JavaTester {
    public static void TestQueueJava(ConnectionManager connManager) {
        // Insert Regular Job.
        WorkInserter.INSTANCE.InsertWork(
            connManager,
            "io.kungfury.endtoend.JavaEmptyJob",
            "",
            "default",
            Instant.now(),
            100
        );

        JavaFunctions jf = new JavaFunctions();

        WorkInserter.INSTANCE.HandleAsynchronouslyJava(
            connManager,
            (Function<Any[], Void> & Serializable) (_obj) -> {
                System.err.println("Hewwo from java lambda empty!");
                return null;
            },
            new Any[] {},
            "default",
            Instant.now(),
            100
        );
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
        WorkInserter.INSTANCE.HandleAsynchronouslyJava(
            connManager,
            (Function <Any[], Void> & Serializable) jf::empty,
            new Any[] {},
            "default",
            Instant.now(),
            100
        );
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
