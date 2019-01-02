# Metrics #

As a preface let us mention that coworker subscribes to the theory that
if you want to monitor something that is specific to a job, you should
monitor in the job itself (e.g. number of times a particular job is
executed, you probably don't want this for every job since total # of
metrics can explode there). So if you're looking for the best way to
monitor one specific set of jobs we recommend creating an abstract class
that extends BackgroundWork, where you can do metrics.

As for monitoring Coworker itself though that's what this doc will cover.
For the most part, there's not too much to monitor in Coworker. It pops
jobs from the database, and works them (again any monitoring being within
the job itself). However, there are a couple knobs in Coworker, and it
would be nice to monitor those (to see if they need to be changed for
example), or if they're causing problems. This is what Coworker exposes
as metrics.

## Configuring Metrics ##

All metrics are handled through [micrometer](https://micrometer.io), and
default to the configured global registry. However, you can pass in your
own registry (if desired, instead of configuring the global registry).

"Why would I want to pass in a custom registry"? you may be asking. This
is because Coworker, expects you to configure tags (such as the environment
Production/Staging/Beta) at the registry level, so that way we don't have
to pass around tags everywhere.

You can configure tags at the registry level through the "commonTags" interface
which they describe on their website [HERE](http://micrometer.io/docs/concepts#_common_tags).

## Metrics Exposed ##

There are two classes that expose a parameter for a custom metric, and here's
the metrics they both provide.

### PgConnectionManager ###

PgConnectionManager exposes the following two metrics:

1. `coworker.pg.query_time` This is a timer that records the amount of time
   each query takes inside of the database. This can be used to get useful
   averages, and alert if coworker is taking too much time to query the DB.
2. `coworker.listen.failure_gauge` This metric is only used when `listenToChannel`
   is called (and it properly tags the metric with the channel being used).
   Here it provides insight into the total number of failures encountered by
   the channel. This will fluctuate up when errors are occuring, and back down
   when errors have recovered (if they do).

### Coworker Manager ###

Coworker Manager exposes the following three metrics:

1. `coworker.garbage.heap.runs` These are a simple count of the number of runs
   for `CleanupGarbage` (this is in actuality the total number of times Coworker
   will go to the DB to actually run DELETEs).
2. `coworker.garbage.heap.received` This is a counter that counts the number
   of jobs that have been requested to be cleaned ("are finished and ready 
   to be DELETE'd, not failed").
3. `coworker.garbage.heap.cleaned` The total number of jobs that have been cleaned
   or "Finished successfully no more stages remaining".
