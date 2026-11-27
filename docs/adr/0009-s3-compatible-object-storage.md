# 0009. S3-compatible object storage

- Status: Accepted
- Date: 2026-10-12

## Context
Users upload avatars now and may upload custom backgrounds later. Files must not live in the database and must survive container rebuilds and server moves.

## Decision
A small storage interface in the backend backed by the S3 API. Production uses Cloudflare R2 (free tier) or MinIO in the Docker Compose stack; local development uses MinIO. The database stores only the object key.

## As built (#15)
`shared/storage`: `ObjectStorage` (put, get, delete) and `S3ObjectStorage` (AWS SDK v2 `s3` 2.57.8, URLConnection HTTP client, path-style, timeouts, checksums only when required). Configuration `meuquarto.storage.*` from `STORAGE_ENDPOINT`, `STORAGE_REGION`, `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`. The local server is not MinIO, because its images are no longer published: see ADR 0021, which also explains how avatars are served from the private bucket.

## Consequences
- Switching providers is configuration, not code.
- Uploads need validation (type, size) and resizing before storage.
- With MinIO on the VPS, backups of its volume are our job; R2 removes that.

## Alternatives considered
- VPS disk volume: simplest, but backups and migration are manual and it does not scale out.
- Database blobs: bloats the database and backups.

## Amendment (2026-11-27, #172): avatars now live in PostgreSQL
The S3-compatible storage was removed to leave fewer technologies to run and explain. `S3ObjectStorage`, the AWS SDK, the `STORAGE_*` settings, the `storage` Compose service and the test container are gone. The `ObjectStorage` interface stays, now implemented by `DatabaseObjectStorage` (JdbcTemplate over the `stored_objects` table, migration V20). Avatars are small (re-encoded, 2 MB limit), so a `bytea` column is enough, and backups are just the database backup. R2 and MinIO are no longer planned. The text above is kept as history.
