# Notifications #

Coworker uses the PostgreSQL notifications in order to stay in sync with jobs. This
is beneficial to doing scans of the table, because notifications are actually their
own queue in the eyes of PostgreSQL. Since notifications are by their nature
ephemeral, being kept in memory we don't have to worry about eating up your precious
IOPS on your DB for checking if we have work.

Unfortunately with LISTEN/NOTIFY there are some downsides that we have to deal with:

  1. We need to check the notification queue _often_. We don't want to fill up your
     db on memory. To ensure we do this we check for notifications in a seperate thread.
     This way if something crazy happens, we build up our job boxes memory, and not pg.
  2. Notification is just a string. Not the safest thing considering how powerful SQLi is.
     As such we can't transmit the entire job over a notification without potentially
     filtering out + breaking the state which "can be anything". As such
     we notify for only parameters we control, and ***know+validate*** aren't SQLi.
     Then query for a specific ID + just the info we need.
  3. We still need to check the table every so often. Because notifications are
     completely ephemeral there is potentially a chance we've missed an event.
     We shouldn't need to do this often, and this should almost always return
     0 (unless we just started up), but you don't want to just crank this number
     up, and never check for work that was missed.

Even considering these downsides, we still believe LISTEN/NOTIFY to be a net-win.
Even more so when you consider the lessened load on the database. Allowing
you to run many many workers, on a smaller db without the fear of your database
tipping over. Even if it's _shared_ with other apps that are doing other things.
