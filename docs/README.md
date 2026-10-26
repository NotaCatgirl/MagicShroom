# MagicShroom design documents

| Document | What it covers |
|---|---|
| [`openapi.yaml`](openapi.yaml) | The hand-written API contract (OpenAPI 3.0): every endpoint, schema, and error response. |
| [`er-diagram.md`](er-diagram.md) | Database tables, relationships, and delete rules. |
| [`auth-sequence.md`](auth-sequence.md) | Google sign-in through Supabase Auth with PKCE, and how the API verifies bearer tokens. |
| [`error-model.md`](error-model.md) | The RFC 9457 Problem Details error shape and which status codes the API returns when. |
| [`adr/0001-supabase-for-database-and-auth.md`](adr/0001-supabase-for-database-and-auth.md) | Why Supabase hosts the database and sign-in. |
| [`adr/0002-monorepo.md`](adr/0002-monorepo.md) | Why the API and the Android app share one repository. |
| [`proposal.md`](proposal.md) | The original project proposal. |
| [`supabase-deployment.md`](supabase-deployment.md) | Supabase setup, schema, and the one-time card import. |
| [`render-deployment.md`](render-deployment.md) | The Render web service that hosts the API. |
| [`screenshots/`](screenshots/) | Screenshots of every Android screen. |

Live API: <https://magicshroom.onrender.com> · Swagger UI: <https://magicshroom.onrender.com/swagger-ui.html>
