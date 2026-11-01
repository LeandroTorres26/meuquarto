# 0021. Local S3 server and avatar serving through the API

- Status: Accepted (complements 0009)
- Date: 2026-11-01

## Context
ADR 0009 chose "R2 or MinIO" behind an S3-compatible interface. When the profile editor (#15) was built, the official MinIO container images were no longer published (the `minio/minio` repository on Docker Hub is gone and the `quay.io/minio/minio` tags cannot be pulled anonymously), so there is no pinned, pullable image to put in Docker Compose or Testcontainers. Separately, the bucket must stay private, yet the public profile has to show the avatar.

## Decision
1. The code speaks only the S3 API (AWS SDK v2, path-style), so the server is configuration. For Docker Compose and the tests we use **RustFS** (`rustfs/rustfs:1.0.3`, Apache-2.0, single container, `/health` endpoint, root credentials from environment). Production remains R2 (or any S3 server): set `STORAGE_ENDPOINT`, `STORAGE_BUCKET`, `STORAGE_REGION` and the two keys. Compose names the service `storage`, not after a product.
2. The bucket is private and has no policy. Avatars reach the browser through `GET /api/public/avatars/{uuid}.jpg` on the API, behind the Next.js `/api` proxy, so the page CSP gets no new host and the bucket address never reaches the client. The name must match exactly the shape we generate (`AvatarKey`); anything else is a 404 without a storage call. A name never changes content (a new upload gets a new name), so responses carry `Cache-Control: public, max-age=31536000, immutable`.
3. The bucket is created on first use if it is missing (production buckets already exist; `HeadBucket` first).

## Consequences
- No new public host, no signed URLs to expire or leak, no CORS.
- Every avatar costs one API request and one storage read the first time a visitor loads it; browsers and Caddy can cache it for a year. If that ever matters, a CDN in front of the endpoint is the next step.
- RustFS is young; the S3 surface we use (put, get, delete, head/create bucket) is the basic one, and the tests exercise it against the real server. Switching server is an image tag.
- Orphans are possible if the process dies between storing an object and committing the row; they are never referenced and can be swept later.

## Alternatives considered
- Build MinIO from source: slow CI and our own image to maintain.
- Garage, SeaweedFS: more moving parts (layout/config files) for the same S3 surface.
- Public bucket or presigned URLs: exposes the storage host to the browser and the CSP, and loses the strict key check.
