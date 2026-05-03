# pg-upgrader: a transient image carrying both the previous-major and the
# current-major PostgreSQL binaries (plus the pg_cron extension for each),
# built only for `pg_upgrade --link` cutovers against the wealthpay_pg_data
# volume. The base image must match the runtime image
# (docker/postgres/Dockerfile) so glibc/collation are identical across every
# image involved in the upgrade — collation drift across a `--link` cutover
# silently corrupts text indexes.
#
# This instance is configured for the PG16 -> PG18 cutover. For the next
# major-version bump (e.g. PG18 -> PG19), update FROM, swap postgresql-16 ->
# postgresql-18, and add postgresql-19-cron alongside the previous one.
# See docs/postgres-upgrade.md § "Upgrade procedure" for where this image
# fits in the major-version-bump checklist.
FROM postgres:18-trixie

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        postgresql-16 \
        postgresql-16-cron \
        postgresql-18-cron \
        locales-all \
    && rm -rf /var/lib/apt/lists/*
