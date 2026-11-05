# Log — Keep the moments that move you

The room is quiet. A lecture is still playing in one tab; a line worth keeping
arrives in another. Tomorrow, the thought may be gone. Log begins at that small,
familiar moment: capture what matters, give it a home, and find it again when the
next idea needs it.

## The story Log is building

Log is imagined as a personal archive for learners, researchers, and curious
people. A note can live beside the message, lesson, or audio that inspired it.
Topics and tags bring order without breaking the flow. In time, alarms can bring
a useful recording back at just the right hour, and a workspace can gather
writing, media, and small study tools in one place.

The guiding idea in the Log Manifesto is local-first ownership: your notes and
media should belong to you, remain useful offline, and be shareable or synced on
your terms. Google sign-in gives the current service an identity; the longer
vision keeps the personal archive in the user's hands.

## Where the camera is today

The current working application is an early Java backend. It provides Google
OAuth sign-in, PostgreSQL-backed user and session handling, and HTTP endpoints
for login, session lookup, logout, and health checks. The Angular, desktop, and
mobile experiences, personal-drive sync, alarms, and media-rich editor described
in the manifesto are future direction—not features claimed as complete here.

## Run the backend locally

Requirements: JDK 25, Maven, and Docker Compose.

From PowerShell, move into `backend`, provide your own local database password
and Google OAuth credentials as environment variables, then start PostgreSQL
and the server:

```powershell
cd backend
$env:DATABASE_PASSWORD = Read-Host "Local PostgreSQL password"
$env:GOOGLE_CLIENT_ID = Read-Host "Google OAuth client ID"
$env:GOOGLE_CLIENT_SECRET = Read-Host "Google OAuth client secret"
docker compose up -d
mvn compile exec:java
```

The default callback URL is
`http://localhost:7070/auth/google/callback`; register it with the same Google
OAuth client. Configure `GOOGLE_REDIRECT_URI`, `FRONTEND_ORIGIN`,
`SESSION_COOKIE_SECURE`, `SESSION_COOKIE_SAME_SITE`, `DATABASE_URL`,
`DATABASE_USER`, and `PORT` through the environment when your setup needs
different values. The backend defaults to database `typeme_db`, user
`typeme_user`, and port `7070`; `DATABASE_PASSWORD` is required and has no
source-code default.

Keep all real credentials in your environment or a secret manager, never in
tracked files. The repository ignores `.env` files, private-key files, Java
build output, and common frontend-generated files. If a real credential was
ever committed or shared, rotate it; ignore rules do not erase Git history.

## Sign-in and session endpoints

- `GET /auth/google` starts Google sign-in.
- `GET /auth/session` returns the signed-in user's public profile or `401`.
- `POST /auth/logout` ends the current session and clears its cookie.
- `GET /health` checks that the backend is running.

Session tokens are random and opaque. The backend sends them only in an
`HttpOnly` cookie and stores their SHA-256 hashes in PostgreSQL. Sessions expire
after 12 hours. Credentialed browser requests should use
`credentials: "include"`; the backend accepts them only from the exact
`FRONTEND_ORIGIN`. Use HTTPS in production, with secure cookies and credentials
stored in the hosting provider's secret store.
