# The Pamoja web assets on arkayenlabs.com

Everything in this folder is served by the **arkayenlabs.com website**, which is
a separate project from this Android repo.

🔴 **The automatic redeploy has never actually worked. Verified 2026-08-21.**

The intent is that the website fetches `terms-pamoja.html` and
`privacy-pamoja.html` from this repo at build time, and a GitHub Action here
redeploys the site whenever either changes on `dev`. That is what the workflow
does. What was never done is **step 2 of its own setup notes**: the
`VERCEL_DEPLOY_HOOK` secret does not exist in this repository.

Measured, not assumed: `gh secret list` returns nothing, and
`gh run list --workflow=redeploy-website.yml` shows exactly **one run ever**,
the push on 2026-08-21, which failed at once with

    VERCEL_DEPLOY_HOOK is not set.

The workflow was added on 2026-08-14 in `1dcdc77`, so nothing has ever
published through it. This paragraph previously read "You no longer hand
anything over to deploy a legal change", which was aspirational and is the
reason a legal page edit sat unpublished without anyone noticing.

**Until the secret exists, every legal change needs a manual redeploy from the
Vercel dashboard.** The prebuild refetches from `dev` on any build, so a manual
deploy does pick up the current files; it just does not happen on its own.

To fix it permanently, do both halves of the setup at the top of
`.github/workflows/redeploy-website.yml`: create the deploy hook in Vercel, then
add it here as the `VERCEL_DEPLOY_HOOK` Actions secret. Confirm with a push that
touches a watched file and a green run.

See [How publishing works](#how-publishing-works) below.

**Canonical host is `www.arkayenlabs.com`.** The apex `arkayenlabs.com`
307-redirects to www. That redirect is why the Android manifest declares only
the www host: Google's App Link verifier does not follow redirects, so a
declared apex could never verify.

---

## The four routes

| URL | Serves | Status |
|---|---|---|
| `https://www.arkayenlabs.com/privacy/pamoja` | `privacy-pamoja.html` | live ✅, confirmed 2026-08-24, mentions Health Connect |
| `https://www.arkayenlabs.com/terms/pamoja` | `terms-pamoja.html` | live ✅, confirmed 2026-08-24, §9 covers subscriptions |
| `https://www.arkayenlabs.com/privacy/pamoja/delete-account` | `delete-account-pamoja.html` | live ✅, confirmed 2026-08-24 — the website-repo change below is done |
| `https://www.arkayenlabs.com/.well-known/assetlinks.json` | `assetlinks.json` | live ✅, all three fingerprints |
| `https://www.arkayenlabs.com/pamoja/join/<code>` | `join-landing.html` | live ✅ |

`<code>` is an opaque group id. The route matches **any** value in that segment
and always returns the same page; the app reads the code from the URL, the page
itself does not.

## The invite page and its link preview (redesigned 2026-10-11, not live)

The live join page and its preview image were still **terracotta** on
2026-10-11 (checked: `og:image` is `https://www.arkayenlabs.com/pamoja-invite-og.png`,
an orange and green card with "GROUP INVITE" in caps; WhatsApp shows it for
every invite link). The site renders **its own copy** of the join page, not
`join-landing.html`: the live HTML was 48 KB of site layout with no
`intent://` button, so the 2026-09-28 Install Referrer change never reached it.

To publish (website repo, after the Shipaton freeze):

1. Replace `/pamoja-invite-og.png` with `web/og/pamoja-invite-og.png`
   (1200x630, Lilac). Same URL, so no meta tag changes. Regenerate it after
   editing `web/og/pamoja-invite-og.html`:

   ```bash
   cd web/og && "/c/Program Files/Google/Chrome/Application/chrome.exe" --headless=new --disable-gpu --hide-scrollbars --window-size=1200,630 --virtual-time-budget=8000 --screenshot="$(pwd -W)/pamoja-invite-og.png" "file:///$(pwd -W)/pamoja-invite-og.html"
   ```

2. Port `join-landing.html` to the site's join route: the Lilac card, the
   `og:` tags in its head, and its script (the `intent://` button that
   carries the invite through a Play install). Keep the security rule at the
   top of that file: the page never shows anything about the group.
3. WhatsApp caches previews per URL for a while. Check a fresh link, e.g.
   one with a new group ID, or use Facebook's Sharing Debugger to refetch.

Verify:

```bash
curl -s https://www.arkayenlabs.com/pamoja/join/test123 | grep -c "intent://"
curl -s https://www.arkayenlabs.com/pamoja-invite-og.png -o /tmp/og.png && python -c "from PIL import Image; print(Image.open('/tmp/og.png').getpixel((5,5)))"
```

The first prints 1 or more; the second prints `(207, 198, 255)`, the Lilac
ground, not the old cream.

## The deletion page's website-repo work — done, verified live 2026-08-24

Added 2026-08-20, and as of 2026-08-24 all three pieces below are confirmed
live by fetching the URL directly (title "Delete Your Pamoja Account | Arkayen
Labs", real content, not a 404 or SPA shell). The section below is kept as the
record of what had to happen, not as outstanding work — re-open it only if the
URL starts 404ing again.

The three changes this needed, all in the **website** repo:

1. Add `delete-account-pamoja.html` to the prebuild fetch list, beside the two
   that are already there.
2. Add the route `/privacy/pamoja/delete-account`, matching the convention the
   sibling app already uses at `/privacy/freshtrack/delete-account`.
3. Nothing else. It renders through the same body-into-layout path.

The path filter in `.github/workflows/redeploy-website.yml` in *this* repo was
already extended to include the file, so edits here republish it like the other
two.

**The open question in this paragraph is now answered, 2026-08-25.** It used to
end "confirm which before assuming automatic republishing works end to end",
because the deletion page could equally have gone live by a manual Vercel
deploy. It had. The `VERCEL_DEPLOY_HOOK` secret did not exist: the repo held
**zero** Actions secrets, which is why both workflow runs on 2026-08-21
(`32505223322` and `32506395057`) failed, and why editing a legal document
published nothing for four days without anyone noticing.

Fixed the same day. The secret was created, and run **`32815746866`** was
dispatched manually and **succeeded in 7s**. The chain works end to end now,
for the first time. If you change a legal document, check the Actions tab for a
green run rather than assuming.

## How publishing works

1. You edit `web/terms-pamoja.html` or `web/privacy-pamoja.html` and push to `dev`.
2. `.github/workflows/redeploy-website.yml` fires and pings a Vercel deploy hook.
3. The website's `prebuild` step refetches both files from `dev` into its source.
4. It renders each document's **body** inside the site's layout, then prerenders
   the result to static HTML.

🔴 **Never write literal HTML tag syntax in these files' header comments.**
The fetch script finds the **first** body tag in the file and extracts from
there, without stripping comments first. On 2026-08-21 the comment at the top of
`delete-account-pamoja.html` spelled out the body tag while explaining this very
mechanism, so extraction started mid-comment and published the remaining
developer notes as visible text on the live page. `privacy-pamoja.html` and
`terms-pamoja.html` have never contained tag syntax in their comments, which is
the only reason this had not happened before.

Check before committing an edit to any of them:

```bash
sed -n '1,/^<html/p' web/delete-account-pamoja.html | grep -c "<body>\|<head>\|<style>"
```

Zero is the only acceptable answer. Worth hardening on the website side too, by
stripping comments before locating the body, so a comment can never decide where
a legal document begins.

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
