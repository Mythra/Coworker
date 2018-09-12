# Artificial Priority #

Artifical Priority is a built in feature of Coworker, in order to ensure a job
gets worked eventually. There is one big problem with many priority based job
frameworks, and that's based around never getting work done. If you have a very
large job queue, and not enough workers it's very possible to get yourself
"stuck" where these low priority jobs will never finish, because higher priority
work is always coming in.

Coworker by it's very nature, makes this problem even more real with the concept
of `yielding`. Because a job can techincally yield forever, it's very possible
that all of your higher priority jobs yield, yield, and yield some more. Leaving
no room for this lower priority work to be done.

In order to combat this, Coworker has implemented an "Artifical Priority". This
artifical priority isn't stored anywhere within the database, but is implemented
in the client itself.

## How Artificial Priority Works ##

Artificial Priority is implemented in a single line check before we attempt
to start locking work. Specifically:

```kotlin
val instant = Instant.now().epochSecond
// ...
workNotifiedAbout.sortBy { work -> work.Priority.toLong() + (instant - work.QueuedAt) }
```

That `sortBy` is all that artifical priority is. When you queue your job you give us
an integer. We take that integer, and turn it into a long. From there we add one priority
for every single second you've been (queued/second since runAt has passed). In this way
we're artifically adding the number of seconds to the priority. So we will actually _eventually_
work a job that has been queued for many many seconds.

It should be noted however, that artifical priority works on a _per-stage_ level. So if you have a job
that is multiple stages it will have to wait for the artifical priority increase
each time it `yields`. Artifical priority is not a permenant patch to not
running enough workers/enough threads on your workers. It is an attempt to lessen
the blow of not running enough infrastructure, so you have time to sort through
changing your code/spinning up more workers.
