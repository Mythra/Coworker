# Coworker #

Coworker is a delayed work runner built for JVM Based languages, written in
kotlin. Coworker started off as an experiment as bringing coroutine ideas
to background work. Allowing you to work on something else if you're say
waiting for an external system.

Specifically Coworker introduces three new concepts:
  * yield - Allowing a piece of work to yield to higher priority work,
    or yield for a set time.
  * Stages - Allowing a piece of work to be more naturally expressed
    with the previously mentioned yielding concepts.
  * `LISTEN`/`NOTIFY` - Using native SQL notifications to allow picking up work
     without constantly querying the DB for new work.

From there Coworker than adopted ideas specifically from [InstJobs][instructure_jobs]
which is a ruby based delayed work runner that has several nice features.
Specifically it brings over the ideas of:

* Self Healing: Coworker if configured will rescheduled work whose underlying
  node died.
* Failed Work Table: Allowing you to track which Work failed.
* N-Strands: Allowing only a certain number of stranded work to run at a time.
* Marginalia: Log where in code a query is happening within the framework.

## Supported Architectures ##

_Tl;dr: PostgreSQL for DBs, Consul for Service Checks, Java/Kotlin officially suported._

Currently Coworker only has support for PostgreSQL DBs. The biggest reason for
this is due to: `LISTEN`/`NOTIFY`. However since postgres has these features
other postgresql only queries have been written that make the workmanager code more
efficient.

As for checking if work are still running, currently the method of doing this
is built into [Consul][consul] service checks. Although there is nothing unique
to consul being used here per say, other architectures are not supported due to
familiarity. If you'd like to see something like zookeeper support please file a PR!


[instructure_jobs]: https://github.com/instructure/inst-jobs
[consul]: https://www.consul.io/
