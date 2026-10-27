# TypeMe backend

## Google OAuth and sessions

Configure these values in the backend's environment. Keep secrets in the hosting
provider's secret/environment-variable settings, not in source files:

| Variable | Required | Local default / purpose |
| --- | --- | --- |
| `GOOGLE_CLIENT_ID` | Yes | Google OAuth client ID |
| `GOOGLE_CLIENT_SECRET` | Yes | Google OAuth client secret |
| `GOOGLE_REDIRECT_URI` | For cloud | Defaults to `http://localhost:7070/auth/google/callback`; register the exact value in Google Cloud |
| `FRONTEND_ORIGIN` | For cloud | Defaults to `http://localhost:4200`; must be the frontend origin only, e.g. `https://app.example.com` |
| `SESSION_COOKIE_SECURE` | For cloud | Defaults to true when the OAuth redirect URI is HTTPS; set to `true` behind HTTPS |
| `SESSION_COOKIE_SAME_SITE` | If cross-site | Defaults to `Lax`; use `None` for a frontend and API on different sites, and require `SESSION_COOKIE_SECURE=true` |
| `DATABASE_URL` | For cloud | JDBC URL; defaults to `jdbc:postgresql://localhost:5433/typeme_db` |
| `DATABASE_USER` | If needed | PostgreSQL user; local default is `typeme_user` |
| `DATABASE_PASSWORD` | For cloud | PostgreSQL password; local default is `typeme_password` |
| `PORT` | If needed | HTTP port; defaults to `7070` |

Register the exact `GOOGLE_REDIRECT_URI` as an authorized redirect URI for the
same Google OAuth client. For local development, start PostgreSQL with
`docker compose up -d`, set the Google variables in the same PowerShell window,
then run `mvn compile exec:java`.

The backend creates opaque, randomly generated session tokens. It sends the
token only in an `HttpOnly` cookie and stores only its SHA-256 hash in PostgreSQL.
Sessions expire after 12 hours. Because session data is in the shared database,
multiple backend instances can validate the same session; in-memory or sticky
sessions are not required. The session table is cleaned of expired entries at
login. Startup creates/migrates the required schema without dropping user or
session data.

The frontend must send credentialed requests to the backend:

- `GET /auth/session` returns the signed-in user's public profile or `401`.
- `POST /auth/logout` deletes the current session and clears its cookie. Send the
  configured frontend origin; browsers do this for normal frontend `fetch` calls.
- Use `credentials: "include"` for both calls.
- Start login by navigating the browser to `GET /auth/google`.
- New backend handlers that protect user data must call
  `SessionService.getCurrentUser(exchange)` and reject a `null` result before
  returning protected data.

If frontend and API are on different subdomains of the same site, `SameSite=Lax`
usually works, but requests still need CORS credentials. If they are on different
sites, use `SESSION_COOKIE_SAME_SITE=None` and HTTPS (`SESSION_COOKIE_SECURE=true`).
The app allows credentialed CORS only from the exact `FRONTEND_ORIGIN`.
Some browsers block third-party cookies even with `SameSite=None`; hosting the API
under the frontend's site or proxying it through the same site is more reliable.

Use HTTPS in production, keep OAuth credentials and database credentials in the
host's secret store, and rotate any credentials that have been exposed.
