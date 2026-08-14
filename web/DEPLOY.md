# The Pamoja web assets on arkayenlabs.com

Everything in this folder is served by the **arkayenlabs.com website**, which is
a separate project from this Android repo.

**You no longer hand anything over to deploy a legal change.** The website
fetches `terms-pamoja.html` and `privacy-pamoja.html` from this repo at build
time, and a GitHub Action here redeploys the site whenever either file changes
on `dev`. Edit, commit, push; the published pages follow within a couple of
minutes. See [How publishing works](#how-publishing-works) below.

**Canonical host is `www.arkayenlabs.com`.** The apex `arkayenlabs.com`
307-redirects to www. That redirect is why the Android manifest declares only
the www host: Google's App Link verifier does not follow redirects, so a
declared apex could never verify.

---

## The four routes

| URL | Serves | Status |
|---|---|---|
| `https://www.arkayenlabs.com/privacy/pamoja` | `privacy-pamoja.html` | live ✅ |
| `https://www.arkayenlabs.com/terms/pamoja` | `terms-pamoja.html` | live ✅ |
| `https://www.arkayenlabs.com/.well-known/assetlinks.json` | `assetlinks.json` | live ✅, all three fingerprints |
| `https://www.arkayenlabs.com/pamoja/join/<code>` | `join-landing.html` | live ✅ |

`<code>` is an opaque group id. The route matches **any** value in that segment
and always returns the same page; the app reads the code from the URL, the page
itself does not.

## How publishing works

1. You edit `web/terms-pamoja.html` or `web/privacy-pamoja.html` and push to `dev`.
2. `.github/workflows/redeploy-website.yml` fires and pings a Vercel deploy hook.
3. The website's `prebuild` step refetches both files from `dev` into its source.
4. It renders each document's **body** inside the site's layout, then prerenders
   the result to static HTML.

Two consequences worth knowing before editing:

- **The `<head>` and `<style>` block are discarded.** The site applies its own
  styling, so changing colours or fonts in these files has no effect on the
  published page. Structure is what matters, not presentation.
- **Everything the reader must see has to be inside `<body>`.** The `<h1>` and
  the `<p class="dates">` line carrying "Last updated" and "Effective" are read
  from the body and published as-is. Delete or rename them and the live page
  loses its heading or its dates, which for a legal document is a real problem.

Markup is sanitised on the way in: `<script>`, `<iframe>`, inline `on*`
handlers and `javascript:` URLs are stripped. Nothing in a legal document should
need them, but it means a compromised or careless commit here cannot put
executable code on the website.

If the fetch fails, the website build **fails** rather than publishing a stale
document. A red deploy is the intended outcome there.

## Requirements that are easy to get wrong

**`assetlinks.json` must be served raw.** Android's verifier is strict:

- `Content-Type: application/json`, not `text/html` or `text/plain`
- HTTP **200 directly**, no redirect of any kind, including http→https or
  apex→www on the final URL
- No auth, no geo-blocking, no bot protection challenge
- Exactly at `/.well-known/assetlinks.json` on the **www** host

The website excludes `.well-known` from its SPA rewrite and sets the
`Content-Type` header explicitly, so this holds. Do not route it through the
app framework.

## Verifying afterwards

Status codes alone prove nothing: a misconfigured SPA returns 200 while serving
an empty shell. Check the content.

```bash
curl -s  https://www.arkayenlabs.com/terms/pamoja   | grep -c "Subscriptions and payments"
curl -s  https://www.arkayenlabs.com/privacy/pamoja | grep -c "Health Connect"
curl -sI https://www.arkayenlabs.com/pamoja/join/test123 | head -1
curl -s  https://www.arkayenlabs.com/.well-known/assetlinks.json
curl -sI https://www.arkayenlabs.com/.well-known/assetlinks.json | grep -i content-type
```

The first two must print `1` or more. The JSON must come back with three
fingerprints and `content-type: application/json`.

A correct terms or privacy page is roughly 44 KB, most of which is the site
shell around the document. Around 2 KB means the deploy is serving the SPA shell
instead of the page.

Then confirm Google itself accepts the asset links:

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

## If the legal pages stop updating

The publishing chain has two parts in two repos. When a change does not appear:

1. Check the Actions tab here for a failed **Redeploy website on legal change**
   run. A missing `VERCEL_DEPLOY_HOOK` secret fails the job loudly.
2. Check the website's Vercel deployments. If none was triggered, the hook URL
   was rotated or deleted and needs recreating.
