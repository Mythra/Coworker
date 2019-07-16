CREATE TABLE IF NOT EXISTS public.delayed_work_credits (
    job_name VARCHAR(255),
    strand_name VARCHAR(255),
    stage INTEGER,
    rolling_average_seconds BIGINT,
    total_jobs BIGINT,
    PRIMARY KEY(job_name, strand_name, stage)
);

CREATE UNLOGGED TABLE IF NOT EXISTS public.delayed_work_credit_use(
    job_name VARCHAR(255),
    strand_name VARCHAR(255),
    stage INTEGER,
    in_use BIGINT,
    PRIMARY KEY(job_name, strand_name, stage)
);
