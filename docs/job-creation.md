# Job Creation #

At this point you should know how to:

1. Run Migrations on Version Changes.
2. Create an instance of a CoworkerManager.
3. Insert Jobs into the Database.

Now we're going to cover creating an actual implementation of a job. You'll
want to extend the `BackgroundKotlinWork`/`BackgroundJavaWork` classes which
wrap a lot of boilerplate for you. However, there are some things that are
needed in order for all of our helper functions to work. In order to make
things as simple as possible for working with interfaces we pass in a set
of parameters at construction time. These will never change without a major
version bump to the library.

The order of these parameters are:

1. A Connection Manager (specifically a io.kungfury.coworker.dbs.ConnectionManager) interface.
2. A Garbage Heap (specifically a io.kungfury.coworker.WorkGarbage) class for buffering deletes.
3. The Work ID (a Long type).
4. The Stage of this current job (an integer type).
5. The Strand of this current job (a String type).
6. The priority of this current job (sans artifical priority) (an Integer).

You may be wondering why these are passed in at construction time. Afterall we could choose
to have the set at runtime and have the helpers unwrap the optional,
but this would been then we would have to deal with the problem
of "What happens if they ever get unset?". Afterall someone might use the interface incorrectly.
We want to make the interface as _hard to screw up as possible_. As such passing them in during
construction makes it harder to screw up. You don't have to know which fields need to be populated
for the helper functions, they just are.

## Defining an Empty Job ##

Now you might rush off, and define a job like so:

```kotlin
class EmptyJob(
    private val connectionManager: ConnectionManager,
    garbageHeap: WorkGarbage,
    id: Long,
    stage: Int,
    strand: String,
    priority: Int
) : BackgroundKotlinWork(garbageHeap, id, stage, strand, priority) {
    override fun serializeState(): String = ""

    override suspend fun Work(state: String) {}
}
```

Or like so with java:

```java
public class EmptyJavaJob extends BackgroundJavaWork {
    public EmptyJavaJob(ConnectionManager _connManager, WorkGarbage garbageHeap, long id, int stage, String strand, int priority) {
        super(garbageHeap, id, stage, strand, priority);
    }

    @Override
    public String getSerializedState() {
        return "";
    }

    @Override
    public void Work(String state) {
        try {
        } catch (Exception e1) {
            // Determine what to do.
        }
    }
}
```

However there is a problem with this! This is one relatively rough part of the interface, but gives us
some nice advantages. You must tell Coworker when your job is finished! So you must call finish work.
As such a complete empty job would look like:

```kotlin
class EmptyJob(
    private val connectionManager: ConnectionManager,
    garbageHeap: WorkGarbage,
    id: Long,
    stage: Int,
    strand: String,
    priority: Int
) : BackgroundKotlinWork(garbageHeap, id, stage, strand, priority) {
    override fun serializeState(): String = ""

    override suspend fun Work(state: String) {
        finishWork()
    }
}
```

and in java:

```java
public class EmptyJavaJob extends BackgroundJavaWork {
    public EmptyJavaJob(ConnectionManager _connManager, WorkGarbage garbageHeap, long id, int stage, String strand, int priority) {
        super(garbageHeap, id, stage, strand, priority);
    }

    @Override
    public String getSerializedState() {
        return "";
    }

    @Override
    public void Work(String state) {
        try {
            this.finishWork();
        } catch (Exception e1) {
            // Determine what to do.
        }
    }
}
```

Now you may be asking, "Why do you do this?", and the answer specifically is to make the
interface for Kotlin/Java easy. For example in "EmptyJavaJob" what happens with that current
code in the example, if finishWork() throws an exception? There's a comment that says
"Determine what to do.".

Well what ends up happening is the JobManager detects that the job wasn't cleaned up, wasn't marked
as finished, and requeues the stage of the job again. This means if you had a temporary database issue,
and did nothing with the exception, it would just requeue again under the hood until it succeeded. All without
you having to write a single line.
