# Web access: serve a web app from the Android app

## Goal

Operate the accounting data from a desktop browser. The phone runs an HTTPS
server on the LAN; the desktop browser talks to it. The phone stays the single
source of truth, so there is no sync problem and data is always fresh.

Releases: first read-only (visualization on a big screen), then writes
(modifications, new data).

Rejected alternative: a separate desktop app (too much effort, needs sync).

## Decisions

| Topic | Decision | Why |
|---|---|---|
| Server library | NanoHTTPD | Tiny, works on API 19. Ktor server needs API 21+. |
| Transport | HTTPS only, self-signed certificate, no HTTP fallback | Plain HTTP exposes data and the passcode to anyone on the network. No fallback so finances can't be served in plaintext by accident. |
| Certificate | Generated on the device on first enable, kept in app-private storage, **generated once** so the fingerprint is stable | SDK has no public API to create X.509 certs, so BouncyCastle `bcpkix`/`bcprov` is needed. A prebuilt keystore in the APK would share the private key with every APK holder. |
| Browser warning | Accepted once. The phone shows the SHA-256 fingerprint; compare it with the browser's certificate details the first time | A self-signed warning only protects if the fingerprint is checked, otherwise an intercepting party could present its own certificate. |
| Auth | Random 8-character passcode typed on a login page, then a session cookie | Keeps the secret out of the URL, history and referrers. |
| QR code | Not needed | The reader is a desktop browser; the URL is short to type. |
| Lifecycle | Foreground service started by a switch in Settings, persistent notification | Required on modern Android; also avoids an always-open port on financial data. |
| JSON | Built-in `org.json` | No extra dependency. |
| Frontend | Vanilla JS, no build step, hash router, assets under `assets/web/`, nothing from a CDN | Must work offline on the LAN; keep maintenance small. |
| Layering | Handlers call repositories and report builders. Never ViewModels | Follows the project's layering rule. |

## Passcode and session details

- 8 characters from a 32-character alphabet (A-Z and 2-9, dropping look-alikes
  `0/O` and `1/I/L`) = 40 bits of entropy.
- Generated with `SecureRandom` on every server start, kept in memory only,
  never on disk. Stopping the server invalidates all sessions.
- Compared in constant time (`MessageDigest.isEqual`).
- Lockout: about 5 failed attempts, then a lockout period (or the passcode is
  invalidated and the server must be restarted). This limit is what makes 8
  characters safe.
- Session cookie: `HttpOnly; Secure; SameSite=Strict`, in-memory session store,
  idle timeout around 30 minutes, explicit "log out" button on the web page.

## What the phone shows when the server starts

- URL with the current IP and port (default port 8443)
- Passcode
- Certificate fingerprint (SHA-256)

IP lookup must cover both Wi-Fi and hotspot/tethering cases.

## Environment caveats

- Phone must be on and on the same network as the desktop. Some Wi-Fi networks
  isolate clients from each other; the phone's hotspot or USB tethering gives a
  private network.
- The phone's IP can change. The certificate is generated once, so the
  fingerprint stays the same; the browser may warn again on a hostname/IP
  mismatch, which can be bypassed the same way. An on-phone local CA that signs
  a fresh certificate per start is the clean fix, left as optional later work.
- TLS on API 19 (KitKat): TLS 1.2 must be enabled explicitly and the supported
  cipher suites are limited. It is not certain they overlap with current
  browsers. The spike answers this. If the handshake can't work on KitKat, the
  fallback is to offer the server only on newer Android versions.
- Battery: the server only runs while the switch is on.

## Plan

Project rules apply to every step: TDD (RED, GREEN, REFACTOR, verify the test
fails first), run `./gradlew assembleDebug testDebugUnitTest` after every
change, one conventional commit per step, generate a testing checklist, no adb
(real-device checks are done by the user). Version bumps are their own commit
plus an annotated `v<versionName>` tag.

### Phase 0: Spike (go/no-go on HTTPS, auth and lifecycle)

- [ ] **0.1 Dependencies.** Add NanoHTTPD and BouncyCastle (`bcpkix`, `bcprov`)
  in `libs.versions.toml` / `build.gradle.kts`. Verify the build passes
  **before any source change**. Check APK size, multidex and API 19
  compatibility.
- [ ] **0.2 `CertificateManager`.** Generate RSA-2048 key and self-signed
  certificate once, store in a keystore in private storage, expose the SHA-256
  fingerprint.
  Tests (Robolectric): generates, reloads, same fingerprint across reloads,
  valid format.
- [ ] **0.3 HTTPS server.** NanoHTTPD with `makeSecure`, TLS 1.2+ only,
  configurable port (8443).
  Test (Robolectric): start on an ephemeral port; a JVM HTTPS client with a
  trust-all manager reaches `/api/ping`.
- [ ] **0.4 Auth.** `PasscodeGenerator`, `SessionStore` (in memory, idle
  timeout), lockout, `POST /login` setting the cookie, guard on everything
  except the login page.
  Tests: plain JUnit for alphabet/length, constant-time compare, lockout,
  session expiry; Robolectric for unauthenticated request returning 401.
- [ ] **0.5 Service and toggle.** Foreground service with persistent
  notification, Settings switch, dialog showing URL, passcode, fingerprint.
  Unit test for IP selection logic; service and notification checked manually.
- [ ] **0.6 One real endpoint.** `/api/balance-sheet` through the repository,
  plus a minimal static page with a login form.
  Test: handler test with in-memory Room.

**Go/no-go check (user, real desktop browser):**

1. Warning appears and the displayed fingerprint matches the browser's.
2. Login works; wrong passcode is rejected and lockout kicks in.
3. Balance sheet data renders.
4. Stopping the server kills the session.
5. If the phone runs KitKat, verify the TLS handshake on that device.

### Phase 1: Server foundation

- [ ] Package `features/webserver/` (server, service, auth, certificate,
  router). No entity; at most a port setting in `AppSettings`.
- [ ] Small router with JSON helpers and consistent error responses.
- [ ] Static file serving from `assets/web/`.
- [ ] Security headers: `Content-Security-Policy`,
  `X-Content-Type-Options`, `Cache-Control: no-store` on API responses.
- [ ] `docs/web-server-feature.md` and a pointer line in `CLAUDE.md`.

### Phase 2: Read-only release

- [ ] Endpoints: accounts, balances, transactions (paged and filtered), the
  three reports with the period selector, trend series, Earn, instruments.
- [ ] Frontend: single-page app, hash router, pages mirroring the app's
  screens; charts in inline SVG or one small vendored library.
- [ ] Refresh button (polling optional later).
- [ ] Each handler tested with Robolectric against an in-memory database; write
  the testing checklist.
- [ ] Version bump in its own commit plus tag.

### Phase 3: Refactor for writes

- [ ] Find business logic currently in Activities/ViewModels (instrument
  transfer base amount, template application, transaction validation, account
  editing) and move it into use-cases or repository methods that both the
  ViewModels and the HTTP handlers call.
- [ ] Pure refactor, no behavior change; existing tests stay green. Estimate
  the size before starting.

### Phase 4: Writes

- [ ] POST/PUT/DELETE endpoints for accounts, transactions (all modes) and
  templates.
- [ ] CSRF: `SameSite=Strict` cookie plus an `Origin`/`Host` check on every
  mutating request.
- [ ] Structured JSON validation errors; writes inside Room transactions.
- [ ] Web forms mirroring the Add Transaction modes.
- [ ] The phone UI updates through its existing LiveData observers.

### Optional later

- Local CA on the phone to avoid repeated browser warnings after an IP change.
- Server-sent events for live updates.
- Auto-stop after a period with no sessions.

## Risks

1. TLS on KitKat (answered by the spike).
2. Background limits: if the OS kills the service the server dies. Acceptable
   if used only while working.
3. Size of the Phase 3 refactor, depends on how much logic sits in the UI
   layer.
4. Frontend maintenance: keep it small so it doesn't become a second app.
