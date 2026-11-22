# Sign in with Google: setup

IAMS shows a **Sign in with Google** button under the password form when `GOOGLE_CLIENT_ID` is set. It is a shortcut, not a
replacement: password login keeps working, and a Google account can only sign in if its **verified email is already registered
in IAMS** (student, lecturer, admin or super admin). Nothing is ever created from a Google account, and the person gets exactly
the roles that email has with a password. An unregistered email is sent back to the login page with a message.

Only the `openid` and `email` scopes are requested; nothing else about the person is read. Basic scopes like these do not
need Google's app verification.

## 1. Google Cloud Console

Labels in the Console change from time to time; the names below are the ones that matter.

1. Open <https://console.cloud.google.com/> and create a project (for example `IAMS`), or pick an existing one.
   A billing account is **not** needed for sign-in.
2. **APIs & Services → OAuth consent screen** (newer Consoles call it **Google Auth Platform**) and fill in:
   - App name `IAMS`, a user support email, and a developer contact email.
   - **Audience / user type: External**, so students and lecturers with any Google account can sign in. (Choose *Internal*
     only if every user belongs to one Google Workspace organisation.)
   - **Authorized domain:** `eduinnovax.com`.
   - **Scopes:** `openid` and `.../auth/userinfo.email` (the non-sensitive ones).
   - **Publishing status: In production.** While it says *Testing*, only the test users you list (max 100) can sign in.
3. **APIs & Services → Credentials → Create credentials → OAuth client ID**:
   - Application type: **Web application**, name `IAMS web`.
   - **Authorized redirect URIs**, exactly (no trailing slash):
     - `https://iams.eduinnovax.com/login/oauth2/code/google`
     - for local testing only: `http://localhost:8080/login/oauth2/code/google`
   - *Authorized JavaScript origins* can stay empty: the sign-in is a server-side redirect.
4. Create it and copy the **Client ID** and the **Client secret**. Treat the secret like a password.

## 2. Dokploy (production)

In the app's **Environment** tab add:

```
GOOGLE_CLIENT_ID=<client id>.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=<client secret>
```

Make sure `APP_PUBLIC_BASE_URL=https://iams.eduinnovax.com` is set (it already is in `docker-compose.yml`). The return address
Google uses is built from it: `APP_PUBLIC_BASE_URL + /login/oauth2/code/google`. Set `GOOGLE_REDIRECT_URI` only if it must differ.
Redeploy the app. To switch Google sign-in off again, empty `GOOGLE_CLIENT_ID`: the button disappears and nothing else changes.

For local development put the same two variables in `adproject/.env`.

## 3. Check it

1. Open `https://iams.eduinnovax.com/login`: there is a white **Sign in with Google** button under the form.
2. Click it with a Google account whose email is registered in IAMS: you land on that account's home page
   (student, lecturer, admin or super admin).
3. Click it with a Google account that is **not** registered: you return to the login page with
   "That Google account is not registered in IAMS...".

## Troubleshooting

| What you see | Cause |
|---|---|
| No Google button | `GOOGLE_CLIENT_ID` is empty or the app was not redeployed. |
| Google shows `Error 400: redirect_uri_mismatch` | The redirect URI in Google Cloud is not exactly `https://iams.eduinnovax.com/login/oauth2/code/google`. Compare it with the one shown in the error. |
| Google shows `Access blocked: ... has not completed the Google verification process` or only test users can sign in | The consent screen is still in *Testing*: publish it (*In production*). |
| Back on the login page with "not registered" | The Google email is not the email stored in IAMS. Add it under Manage Users, or ask the person to sign in with their registered address. |
| Back on the login page with "could not confirm that email address" | Google reports the email as unverified; the person must verify it with Google. |
| Back on the login page with "did not complete" | The sign-in expired, was cancelled, or the client secret is wrong. Check the app logs for `Google sign-in failed`. |
