# Deployment

## Supabase project setup

1. Create a project on the account `slowspeedguy@gmail.com` (not
   hardcoded anywhere in the app — you do this manually in the Supabase
   dashboard, per the brief).
2. Link the CLI and push the schema:
   ```bash
   supabase login
   supabase link --project-ref <your-project-ref>
   supabase db push          # applies supabase/migrations/*.sql in order
   ```
3. **Auth → Providers → Google**: enable it, paste the Google OAuth client
   ID/secret (see "Google OAuth" below). Copy the callback URL Supabase
   shows you (`https://<project-ref>.supabase.co/auth/v1/callback`) — you
   need it for the Google Cloud Console step.
4. **Auth → URL Configuration**: set Site URL to your Vercel production
   URL, and add redirect URLs for both clients:
   - `https://<your-vercel-domain>/auth/callback`
   - `http://localhost:5173/auth/callback` (local dev)
   - `personalstrava://auth-callback` (Android)
5. **Project Settings → API**: copy the Project URL and `anon` `public` key
   — these go into `web/.env.local` and `android/local.properties`. Copy
   the `service_role` key separately — it only ever goes into Edge Function
   secrets, never a client `.env`.
6. **Edge Functions**:
   ```bash
   supabase secrets set RESEND_API_KEY=... RESEND_FROM_ADDRESS=...
   supabase functions deploy send-report
   supabase functions deploy generate-share-card
   ```
   Schedule `send-report` (weekly/monthly) with Supabase's cron scheduler
   (Dashboard → Edge Functions → your function → Cron) or `pg_cron` +
   `pg_net` calling the function URL — either works; the function itself is
   stateless and idempotent-per-call (it reads `profiles.*_report_enabled`
   fresh every invocation).

## Google OAuth configuration

1. Google Cloud Console → APIs & Services → Credentials → Create Credentials
   → OAuth client ID → **Web application**.
2. Authorized redirect URIs: add the Supabase callback URL from step 3
   above (`https://<project-ref>.supabase.co/auth/v1/callback`) — this is
   the *only* redirect URI Google needs, because both clients go through
   Supabase's hosted OAuth flow rather than talking to Google directly.
3. Paste the resulting client ID + secret into Supabase's Google provider
   config (previous section, step 3).
4. No separate Android OAuth client is needed — the Android app opens the
   system browser/Custom Tab for the Google consent screen (via
   `supabase-kt`'s `Auth` plugin, configured with the `personalstrava`
   deep-link scheme in `SupabaseClientProvider.kt`) and Supabase handles the
   token exchange the same way it does for web.

## Vercel deployment

1. Import the GitHub repo into Vercel, set the **Root Directory** to `web/`
   (this is a monorepo — Vercel needs to know the web app isn't at the repo
   root).
2. Build command: `npm run build` (auto-detected). Output directory:
   `dist` (auto-detected).
3. Environment Variables (Vercel dashboard → Project → Settings →
   Environment Variables): `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY`.
   Never add `SUPABASE_SERVICE_ROLE_KEY` or `RESEND_API_KEY` here — those
   belong only to Supabase Edge Function secrets.
4. `web/vercel.json` (already committed) handles SPA rewrites so client-side
   routes survive a hard refresh, plus baseline security headers.
5. After the first deploy, take the production URL back to Supabase Auth →
   URL Configuration and add `https://<your-domain>/auth/callback` to the
   redirect allow-list (step 4 of the Supabase section above) — sign-in
   will silently redirect-fail until this is done.
6. PWA installability works automatically once deployed over HTTPS (Vercel
   default) — `vite-plugin-pwa` generates the manifest and service worker
   at build time, no extra Vercel config needed.

## Environment variable reference

See the root `.env.example` for the canonical list and which surface each
variable belongs to (web bundle, Android local build, or server-only Edge
Function secret). The short version: anything prefixed `VITE_` or matching
`SUPABASE_URL`/`SUPABASE_ANON_KEY` is safe in a client because RLS is the
actual security boundary, not secrecy of the anon key. `SUPABASE_
SERVICE_ROLE_KEY` and `RESEND_API_KEY` are the two values that must never
appear in a committed file, a client bundle, or a `git log` — see the root
`.gitignore` for what's excluded.
