# 0009. S3-compatible object storage

- Status: Accepted
- Date: 2026-10-12

## Context
Users upload avatars now and may upload custom backgrounds later. Files must not live in the database and must survive container rebuilds and server moves.

## Decision
A small storage interface in the backend backed by the S3 API. Production uses Cloudflare R2 (free tier) or MinIO in the Docker Compose stack; local development uses MinIO. The database stores only the object key.

## Consequences
- Switching providers is configuration, not code.
- Uploads need validation (type, size) and resizing before storage.
- With MinIO on the VPS, backups of its volume are our job; R2 removes that.

## Alternatives considered
- VPS disk volume: simplest, but backups and migration are manual and it does not scale out.
- Database blobs: bloats the database and backups.
