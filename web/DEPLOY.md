# Deploying the Pamoja web assets to arkayenlabs.com

Everything in this folder is served by the **arkayenlabs.com website**, which is
a separate project from this Android repo. This file is the brief to hand to
whoever (or whatever) works on that site.

**Canonical host is `www.arkayenlabs.com`.** The apex `arkayenlabs.com`
307-redirects to www. That redirect is why the Android manifest declares only
the www host: Google's App Link verifier does not follow redirects, so a
declared apex could never verify.

---

## The four routes

| URL | Serves | Status today |
|---|---|---|
| `https://www.arkayenlabs.com/privacy/pamoja` | `privacy-pamoja.html` | live ✅ |
| `https://www.arkayenlabs.com/terms/pamoja` | `terms-pamoja.html` | **404** ❌ |
| `https://www.arkayenlabs.com/.well-known/assetlinks.json` | `assetlinks.json` | live, needs updating |
| `https://www.arkayenlabs.com/pamoja/join/<code>` | `join-landing.html` | live ✅ |

`<code>` is an opaque group id. The route must match **any** value in that
segment and always return the same page; the app reads the code from the URL,
the page itself does not need to.

## Requirements that are easy to get wrong

**`assetlinks.json` must be served raw.** Android's verifier is strict:

- `Content-Type: application/json`, not `text/html` or `text/plain`
- HTTP **200 directly**, no redirect of any kind, including http→https or
  apex→www on the final URL
- No auth, no geo-blocking, no bot protection challenge
- Exactly at `/.well-known/assetlinks.json` on the **www** host

If the site framework rewrites unknown paths to an SPA shell, `.well-known`
must be excluded, or the verifier receives HTML and silently fails.

**The legal pages are plain static HTML.** They carry their own styling and
need no layout wrapper, header or footer injected. Do not reformat or
"improve" the copy; both documents are legal text that the app links to
directly and that Play reviewers read.

## Verifying afterwards

```bash
curl -sI https://www.arkayenlabs.com/terms/pamoja | head -1
curl -sI https://www.arkayenlabs.com/privacy/pamoja | head -1
curl -sI https://www.arkayenlabs.com/pamoja/join/test123 | head -1
curl -s  https://www.arkayenlabs.com/.well-known/assetlinks.json
curl -sI https://www.arkayenlabs.com/.well-known/assetlinks.json | grep -i content-type
```

All four should be `HTTP/2 200`. The JSON must come back with three
fingerprints and `content-type: application/json`.

Then confirm Google itself accepts it:

```
https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://www.arkayenlabs.com&relation=delegate_permission/common.handle_all_urls
```

And on a device with the app installed:

```bash
adb shell pm verify-app-links --re-verify com.arkayenlabs.pamoja
adb shell pm get-app-links com.arkayenlabs.pamoja
```

Look for `verified` next to `www.arkayenlabs.com`.

## About the three fingerprints

`assetlinks.json` lists three SHA-256 certificates, all for
`com.arkayenlabs.pamoja`. Do not remove any of them:

| Fingerprint starts | Certificate | Why it is listed |
|---|---|---|
| `50:EE:34:74…` | Google's **Play App Signing** key | What every build installed from Play is signed with. The one that matters in production |
| `09:82:DF:6F…` | local **debug** keystore | So App Links work while developing |
| `EC:3F:F2:8C…` | **upload** keystore | So a release APK built locally and sideloaded behaves like a real one. Without it, sideloaded release builds fail link verification and Google sign-in together, which looks like two unrelated bugs |
