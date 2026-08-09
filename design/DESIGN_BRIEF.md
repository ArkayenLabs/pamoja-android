# Pamoja, CONTINUATION of the app design

You already designed the Pamoja mobile app UI (shared-goal fitness accountability app: small private groups commit to a combined weekly step target, Mon to Sun, steps pooled and shown on a leaderboard). **That design is approved and we are building it.**

**Keep everything exactly as you designed it.** The visual language, palette, typography, spacing, shape, iconography and motion are all locked. Do not redesign anything.

This brief covers only what is **missing or has changed**. Design these in the same system, in **both light and dark**.

> **Two writing rules for everything you produce, including UI copy:**
> **No emoji anywhere.** Not as icons, list markers, decoration, or in notification copy. Use icons only.
> **No em dashes.** Use full stops or commas.

---

## 1. CORRECTION, the invite screen changes

The previous brief told you to make **"Share on WhatsApp"** the prominent primary action. **That was wrong. Remove it entirely.**

WhatsApp is not installed everywhere, it presumes the user's messaging app, and the system share sheet already surfaces WhatsApp first for people who have it.

**Redesign the Invite / Group Created screen with:**
- **Primary action: "Copy link"**, with its copied confirmation state
- **Secondary action: "Share"**, opens the system share sheet
- **A QR code**, prominently displayed, for joining in person. This is a family and office product, people are often in the same room
- **A short human readable invite code** shown below the QR. 8 characters, unambiguous alphabet with no I, L, O or U, so it can be read aloud or typed
- A **"Regenerate code"** action for the group admin

## 2. NEW, QR and manual join flows

- **QR scanner screen**, camera view with a framing guide, plus its states: scanning, code detected, invalid code, camera permission denied
- **"Enter code manually" screen**, an 8 character code input (segmented or single field, your call), with validating, invalid, expired and success states

## 2B. NEW, the deep link landing experience (important)

Tapping an invite link, or scanning the QR, **opens the Pamoja app directly**. The user never copies or pastes anything. Design what they land on:

- **Join preview screen**, shown the instant a link or QR opens the app. Shows the group *before* they commit: **group name, member count and cap, and the weekly goal**. Actions: **Join** and **Cancel**.
  - **Do not show individual member names or avatars on this screen.** Anyone holding a forwarded link can reach it, and real people's names should not be exposed before the viewer has actually joined. Group name and member count are enough to confirm you have the right group.
- **Link opened while signed out or not yet onboarded**, the user completes onboarding first, then lands on the join preview automatically with the group remembered. Show that the invite is being held: *"You have been invited to a group. Finish setting up to join."*
- **Link opened when already a member**, go into the group with a brief confirmation that they were already in it, not an error and not silence.
- **Link opened when the group is full**, the "group full" dead end state.
- **Invalid, expired or revoked link**, clear dead end with a way forward: *"Ask your group admin for a new link."*
- **Loading**, the moment between the app opening and the group details resolving.
- **App not installed**, the simple web fallback page the link opens in a browser, which sends the user to the Play Store and preserves the invite.

## 2C. NEW, real authentication (this replaces the old anonymous flow)

The app previously created a silent anonymous account, and the "Welcome back" screen was decorative. It is moving to **real accounts** with three sign-in methods. Design the full set.

**Sign-in methods, in this order of prominence:**
1. **Continue with Google**, one tap, expected to be the most used
2. **Continue with phone**, primary preference in India, OTP based
3. **Continue with email**, password based

**Screens to design:**

- **Auth landing**, the choice of the three methods, plus a "why do I need an account" reassurance. Keep it warm and short. This replaces the old fake sign-in screen
- **Phone entry**, country code selector defaulting to India, phone number field, validation states
- **OTP entry**, 6 digit code input, auto-advance between boxes, resend timer countdown, "wrong number, go back" escape, and states for: sending, sent, verifying, wrong code, expired code, too many attempts
- **Email sign up**, email and password with strength indication and inline validation
- **Email sign in**, plus **forgot password** and its "check your inbox" confirmation
- **Account linking prompt**, shown to someone already using the app anonymously: *"Save your groups. Add a sign-in method so you do not lose them if you change phones."* This is important, without an account their groups vanish on uninstall
- **Signed-in state in Settings**, showing which method is connected, with the option to add another

**States needed throughout:** loading per button, network failure, invalid credentials, account already exists with a different method, phone number already in use, rate limited.

**One critical detail:** signing in must feel like it *keeps* what you already have, never like starting over. Copy should reinforce that. Someone who created a group anonymously and then signs in must not fear losing it.

## 3. NEW, the complete state matrix

The previous brief covered loading, empty, error, offline, permission denied and overflow. Production needs more. **Design every one of these**, using a consistent visual language across the app:

| State | What it needs |
|---|---|
| **Loading, initial** | Skeleton matching the real layout, never a bare centred spinner |
| **Loading, refresh** | Content stays visible, subtle indicator on top |
| **Loading, action** | Progress *inside* the button, button disabled, never a full screen block |
| **Empty** | Illustration, encouraging copy, clear primary action |
| **Error, retryable** | Plain language cause and a Retry button |
| **Error, terminal** | What happened and what to do instead |
| **Offline** | Persistent banner, cached content, clear "this is stale" signal |
| **Stale data** | "Last updated 2 hours ago" treatment |
| **Slow network** | Skeleton persists, then after about 10s "still trying, cancel" |
| **Partial failure** | Show what loaded, mark what failed, retry just that part. For example members loaded but steps did not |
| **Permission revoked mid session** | Health access was granted, then turned off. Persistent but not annoying |
| **Session expired** | Explain plainly, route to sign in |
| **Form validation** | **Inline error beneath the field**, on blur, never a snackbar on submit |
| **Disabled** | Visually clear *and* communicates why it is disabled |
| **Success** | Explicit confirmation for anything that changed data |
| **Conflict** | "You are already in this group", handled gracefully, not as an error |

**Also design these as reusable components**, they will become a shared component library:
Empty state block, error state block, loading skeleton set, offline banner, snackbar (success, error, info variants), text field with validation states (default, focused, error, disabled, with helper text).

**Specific screens needing state work:**
- **Group dashboard**, group with zero steps logged yet and it must still feel hopeful, members loaded but steps failed, group deleted while you are viewing it
- **Home**, load failed with retry, offline with cached groups
- **Join**, validating, invalid code, expired link, **group full**, already a member
- **Create group and Profile setup**, inline validation on every field, submitting, failed with retry
- **Settings**, deleting account in progress, delete failed, offline with destructive actions disabled and explained

## 4. NEW, monetization screens

The app will be **freemium, subscription based, no ads ever**. Design:

- **Paywall screen**, monthly versus annual with annual as the hero carrying a free trial, clear value comparison, honest and warm rather than pushy. Shown *after* the user's first steps land on the leaderboard, never before
- **Upgrade prompts and limit reached states**, what a free user sees when they hit a limit: trying to create a second group, invite a sixth member, or open history beyond the current week. These must feel like an invitation, not a wall
- **Premium indicator**, a subtle marker showing a user or group is on premium
- **Manage subscription** row in Settings
- **Subscription states**, active, in free trial with days remaining, **payment failed or grace period** (a third of Android subscription cancellations are failed payments, so this screen genuinely matters), and expired

Planned free versus premium split, for the limit states:

| | Free | Premium |
|---|---|---|
| Groups | 1 | Unlimited |
| Group size | up to 5 | up to 20 |
| History | current week | full history and weekly recaps |
| Stats | basic leaderboard | charts, trends, records |
| Streaks and badges | streak count only | full achievements |
| Targets | presets only | fully custom |

## 4B. NEW, Profile and a complete Settings screen

There is currently **no profile screen at all**. A user sets their name, photo, age, height and weight during onboarding and can never change them again. Design:

**Profile header**, sits at the **top of Settings**: avatar, display name, and one summary stat such as member since, total steps, or current streak. Tapping it opens the profile editor.

**Edit Profile screen**, name, photo with an avatar picker and upload state, age, height, weight. All editable. Include the empty avatar and photo selected states.

**Account section on the profile**, now that sign-in is real: which method is connected (Google, phone or email), the associated address or number, an option to add a second method, and change password where email is used. Design the signed-in and the not-yet-linked variants, since an existing anonymous user will see the second one.

**Settings screen, full structure.** Design all of these rows and sections:

- **Profile header** (above)
- **Preferences**, **Theme selector: System, Light, Dark** (the app supports both themes but currently gives the user no way to choose), notification settings, units (metric or imperial), week start day
- **Health**, Health Connect status (connected, disconnected, unavailable), "Last synced 12 minutes ago", and a **Sync now** action
- **Subscription**, current plan, Manage subscription, Upgrade (see §4)
- **Support**, Help and FAQ, Contact support, Report a problem
- **Legal**, Privacy Policy, Terms of Use, **Open source licences**
- **Data**, Export my data, Delete account
- **Account**, Log out
- **About**, app version and build number

**Notification settings screen**, per category toggles (Step reminders, Goals and achievements, Group activity, Weekly recap), a quiet hours time range, and a frequency control. Include the state where system level notification permission is denied, with a link to system settings.

## 4C. NEW, notification design

Notifications are the retention engine and currently have no design at all. Design the **notification content system**, not just visuals:

- **Notification anatomy**, how a Pamoja notification looks in the tray: icon, title, body, expanded state, and any action buttons
- **A monochrome small icon**, Android requires a white silhouette. A full colour icon renders as a white blob
- **Notification types**, each with example copy:
  - Group hit the weekly goal (the emotional payoff, make this one special)
  - Someone joined your group
  - Someone overtook you on the leaderboard
  - Group is close to the goal with a day or two left
  - You were cheered or nudged by a teammate
  - Weekly recap
  - Streak at risk
  - Daily step reminder
- **In app notification or activity centre**, where past notifications live once dismissed
- **Permission primer screen**, shown *before* the system notification dialog, explaining the value of allowing them

**Two hard copy rules for notifications:**

**Keep them short.** Android truncates around 40 characters of title and 55 of body on most devices. If it does not fit in the tray it does not exist.

**Warm and encouraging, never shaming.** Never reference being last, falling behind, or compare a user unfavourably to the group. The person at the bottom of a step leaderboard may be ill, injured, or having a hard week. Roast the situation if you like, the sofa, the weather, an empty group. Never roast the person.

## 5. NEW, small additions

- **Pull to refresh** treatment on Home and the Group dashboard
- **Double tap protection**, how a submit button looks the instant it is pressed
- **A "what is disabled and why" pattern**, used for offline actions and free tier limits

---

## Rules (unchanged)

- Both **light and dark** for everything
- Dark stays **rich and layered, never pure black**
- **Icons only, never emoji**, not as icons, list markers, or decoration
- **No em dashes** in any copy
- Match the existing design system exactly. This is a continuation, not a redesign
