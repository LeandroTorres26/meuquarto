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
cp .env.example .env   # then set POSTGRES_PASSWORD
docker compose -f infra/docker-compose.yml --env-file .env up --build
```

Web: http://localhost:3000 · API health: http://localhost:8080/api/health

Development commands are listed in [CLAUDE.md](CLAUDE.md#commands).

Optional: block commits that contain secrets (uses a local `gitleaks` or Docker):

```bash
git config core.hooksPath .githooks
```

## Documentation

Start at [docs/briefing.md](docs/briefing.md), then [requirements](docs/requirements.md), [architecture](docs/architecture.md), [decisions](docs/adr/README.md) and [security](docs/security.md).
