# Backend image deployment

Pull requests run the backend Gradle build and build the Docker image without publishing it. A push to `main` runs the same tests, publishes `ghcr.io/notacatgirl/magicshroom-api` with both the commit SHA and `main` tags, then asks Render to deploy that exact SHA-tagged image.

Before merging this workflow to `main`, configure these external settings:

1. In GitHub repository Settings → Secrets and variables → Actions, add `RENDER_DEPLOY_HOOK_URL` with the deploy hook from the Render API service's Settings page. The hook is secret; do not commit it.
2. In Render Workspace Settings → Container Registry Credentials, add a credential named `ghcr-read` for `ghcr.io`. Use your GitHub username and a fine-scoped token with `read:packages` access to the image package. The `render.yaml` image credential references this name.
3. Set `SUPABASE_AUTH_ADMIN_KEY` in the Render service environment. This server-only key is needed for permanent Supabase Auth user deletion; never put it in the Android app or repository.

The GHCR package can remain private when Render has the `ghcr-read` credential. CI uses GitHub's short-lived `GITHUB_TOKEN` to publish; that token is not used by Render.
