CREATE TABLE IF NOT EXISTS public.delayed_work_credits (
    strand_name VARCHAR(255) PRIMARY KEY,
    rolling_average_seconds BIGINT,
    total_jobs BIGINT
);

CREATE UNLOGGED TABLE IF NOT EXISTS public.delayed_work_credit_use(
    strand_name VARCHAR(255) PRIMARY KEY,
    in_use BIGINT
);
