# Strands #

Strands are a fairly unique concept if you haven't dealt with them before, and
they vary slightly from their inspiration inside of [Inst-Jobs](https://github.com/instructure/inst-jobs).

## Differences Between Inst-Jobs, and Coworker ##

Inside of [Inst-Jobs](https://github.com/instructure/inst-jobs) strands act as not only a way
to limit the number of jobs running based on an ad-hoc tag, but they also act as a
sort of FIFO queue. Where there's a series of `nextInStrand` flags that get flipped
as jobs get worked on one after one. This concept besides requiring a rather complex
trigger function, also doesn't apply cleanly to our idea of "yielding".

As such if you've dealt with [Inst-Jobs](https://github.com/instructure/inst-jobs) version
of stranding you should know the biggest difference is we only bring over the concept
of N-Strands. This means the "nextInStrand" flag is completely gone. Instead we rely
on the priorities of jobs only to determine which job needs to be worked next (*Note:
if you're wondering about how we ensure a job gets worked eventually, I recommend
reading the internal doc on "Artifical Priority"*).

## How N-Strands Work ##

The "N-Strand" mechanic of Coworker allows you to limit the number of jobs running
based on an ad-hoc tag (or "strand"). This feature in and of itself is very powerful. Specifically
it allows you to do things like:

  * Singleton Jobs. Set the NStrand to "1" for a particular strand tag, and this gives
    you singleton like mechanics. Ensuring only one instance is running at a time.
  * Limiting jobs based on tenant. If you run a multi-tenant application where some tenants
    are huge, and some are very small you can use strands as a way to ensure that
    your giant tenants don't overrun your job queue, and your downstream database.
  * Limting the type of a job that's running. If you have one job that reaches a third-party
    and the third-party can't handle your scale of traffic, you could use strands as a way
    to limit the amount of jobs currently hitting that third party provider.

And many more.

N-Strand configuration is really simple. All it is a map of `<Regex, MaxNumberOfJobs>`.
It should be noted although the nstrand configuration is regex based, it still applies
on the full tag themselves. For example if you have the regex:

`account:.* -> 5`

And you have 10 jobs queued with the tag:

`account:asd`

And another job queued with the tag:

`account:sad`

Coworker will attempt to run 5 jobs of `account:asd`, and the one job: `account:sad` at the
same time.

The N-Strand mapping will take the first mapping into account. Meaning inside your map you
want most specific to least specific. E.g.:

```
account:.*:super_heavy_job -> 3
account:.* -> 5
```

This would properly limit: `account:10:super_heavy_job` to 3, while still allowing: `account:10:non_heavy_job`
to run 5 instances at a time.
