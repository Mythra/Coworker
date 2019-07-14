CREATE TABLE IF NOT EXISTS public.delayed_work_credits (
    strand_name VARCHAR(255),
    stage INTEGER,
    rolling_average_seconds BIGINT,
    total_jobs BIGINT,
    PRIMARY KEY(strand_name, stage)
);

CREATE UNLOGGED TABLE IF NOT EXISTS public.delayed_work_credit_use(
    strand_name VARCHAR(255),
    stage INTEGER,
    in_use BIGINT,
    PRIMARY KEY(strand_name, stage)
);
