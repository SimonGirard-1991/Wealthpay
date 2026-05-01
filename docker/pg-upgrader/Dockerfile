# pg-upgrader: a transient image that carries both PG16 and PG18 binaries plus
# the pg_cron extension for each, used only to run `pg_upgrade --link` against
# the wealthpay_pg_data volume during the PG16 -> PG18 cutover (see
# docs/postgres-18-migration-plan.md, Phase 2.9 and Phase 4a). Trixie matches
# the runtime image (docker/postgres/Dockerfile) so glibc/collation are
# identical across all images involved in the upgrade.
FROM postgres:18-trixie

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        postgresql-16 \
        postgresql-16-cron \
        postgresql-18-cron \
        locales-all \
    && rm -rf /var/lib/apt/lists/*
