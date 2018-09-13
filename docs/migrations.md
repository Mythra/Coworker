# Migrations #

Since Coworker uses a SQL Store to back it's data, it also needs certain tables
created. Aka we need to run migrations. Coworker decides to leave this up to
the user of coworker as opposed to doing something in code ourselves. This is
so you as a user can use whatever sort of migration strategy you want. Whether
you want to use something as part of your build process like [Flyway](https://flywaydb.org/),
some out of band CLI like [migrate](https://github.com/golang-migrate/migrate), or just
manually apply these SQL Files when you need be. Coworker wants to support them all.

As such coworker keeps local copies of the migrations inside of it's resources
folder, and distributes them as part of the release on Github, documenting
them in our Changelog file. We also list the full contents of each migration
in this document, and which version is required to use them.


## Migration #1: Required Version(1.0.0) ##

This is the initial migration for Coworker, and scaffolds the DelayedJob+FailedJob tables.

```sql
CREATE TABLE IF NOT EXISTS public.delayed_work (
	id BIGSERIAL PRIMARY KEY,
	locked_by VARCHAR(255),
	created_at TIMESTAMP,
	run_at TIMESTAMP,
	stage INTEGER,
	priority INTEGER,
	work_unique_name VARCHAR(255),
	strand VARCHAR(255),
	state TEXT
);

CREATE TABLE IF NOT EXISTS public.failed_work (
	id BIGINT PRIMARY KEY,
	failed_at TIMESTAMP,
	stage INTEGER,
	work_unique_name VARCHAR(255),
	failed_msg TEXT,
	strand VARCHAR(255),
	state TEXT,
	run_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS index_delayed_work_on_locked_by ON public.delayed_work(locked_by) WHERE locked_by IS NULL;
CREATE INDEX IF NOT EXISTS index_delayed_work_on_strand ON public.delayed_work(strand);
```
