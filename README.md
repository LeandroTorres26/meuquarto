# meuquarto

A link-in-bio for media taste: one public profile showing the films, series, anime, manga, books, games and music someone loves, synced from the platforms they already use (Letterboxd, AniList, Last.fm, Steam, Goodreads) or picked by hand. Academic project (Programação Orientada a Objetos, Fatec Baixada Santista).

## Stack

- `backend/`: Java 21, Spring Boot 4, Maven, PostgreSQL
- `frontend/`: Next.js 16, TypeScript
- `infra/`: Docker Compose
- `docs/`: requirements, architecture, ADRs, data model, security, design

## Run locally

Requirements: Docker Desktop. For development outside Docker: JDK 21 and Node 22.

```bash
cp .env.example .env   # then set POSTGRES_PASSWORD, STORAGE_ACCESS_KEY and STORAGE_SECRET_KEY (random values)
docker compose -f infra/docker-compose.yml --env-file .env up --build
```

Web: http://localhost:3000 · API health: http://localhost:8080/api/health · Mailpit (e-mails sent by the API, such as the password reset link): http://localhost:8025

E-mail is sent over SMTP configured by the `MAIL_*` variables (see `.env.example`). The compose stack points them at Mailpit, which catches every message locally. In production, point the same variables at a transactional provider's SMTP and set `SITE_URL` to the public https origin (ADR 0012).

Development commands are listed in [CLAUDE.md](CLAUDE.md#commands).

Optional: block commits that contain secrets (uses a local `gitleaks` or Docker):

```bash
git config core.hooksPath .githooks
```

## Demo

`scripts/demo.sh` fills a running local stack with a demo profile through the public API, the same way the browser does: it signs up (or signs in when the account exists), links the accounts you give, runs a manual sync for each, highlights the first 4 items of every category and prints the profile address. Run it from Git Bash, WSL or any POSIX shell with `curl`.

```bash
DEMO_PASSWORD='a-long-throwaway-password' scripts/demo.sh   --email demo@example.com --slug demo   --anilist SomePublicAniListUser --letterboxd SomePublicLetterboxdUser
# optional: --add ANIME:frieren (catalog search, adds the first result), --base http://localhost:3000
```

`BASE` (or `--base`) defaults to http://localhost:3000. Use a throwaway password: the one you pass is only sent to your local stack, and nothing is stored (the cookie jar is a temp file removed on exit). A sync inside its 15-minute cooldown, a rate limit (HTTP 429) or an unknown platform user is reported and the script carries on with the rest. Open the printed address afterwards, for example http://localhost:3000/demo, and `?item=<id>` on it opens an item.

## Documentation

Start at [docs/briefing.md](docs/briefing.md), then [requirements](docs/requirements.md), [architecture](docs/architecture.md), [decisions](docs/adr/README.md) and [security](docs/security.md).
