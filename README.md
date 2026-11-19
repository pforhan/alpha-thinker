# Alpha Thinker

Lightweight project planning app with iterative question-and-answer synthesis. Helps users plan projects through structured and iterative analysis of their project ideas.

Alpha Thinker is available in two versions:
- **Alpha Thinker Edge**: Powered by an LLM engine for automated question generation and synthesis.
- **Alpha Thinker Lite**: A lightweight version using a fixed set of seed questions and user-driven manual planning.

## Building

The app is a Kotlin Multiplatform / Compose Multiplatform project. Shared UI and domain code (Compose screens, Room persistence, question generation) lives in the `shared/` module. Each platform's app entry point lives in its own thin module that depends on `shared/`; the Android entry point is `androidApp/`. Build and run it with standard Gradle tasks from the root of the repository.

### Prerequisites

- **JDK 21**: Required for the KMP shared module and Android app.
- **Android Studio / Xcode**: For platform-specific builds.

### Android

The shared module (`shared/build.gradle.kts`) compiles for the Android target via the Android-KMP library plugin; the `androidApp/` module (Activity, Application, manifest) compiles with AGP's built-in Kotlin and depends on `:shared`.

```bash
./gradlew :androidApp:assembleDebug   # build the debug APK
./gradlew :androidApp:installDebug    # install the debug APK on a connected device or running emulator
./gradlew :shared:allTests  # run tests for all targets (Android host, desktop, JS, wasmJS, and iOS)
./gradlew :androidApp:lint            # run the Android linter
```

### Web

The web target is enabled: `shared/` exposes the `wasmJs` (and legacy `js(IR)`) targets, and the `webApp/` module is the web entry point. The shared UI renders with the Skia/Canvas backend via Compose for Web.

```bash
./gradlew :webApp:wasmJsBrowserDevelopmentRun   # start the dev server (http://localhost:8080)
./gradlew :webApp:wasmJsBrowserDistribution     # build the production bundle
./gradlew :webApp:wasmJsBrowserProductionRun    # serve the production bundle
```

The production bundle lands in `webApp/build/dist/wasmJs/productionExecutable/` — this directory is the static site to deploy (upload/copy its contents as-is, preserving paths).

#### Web deployment requirements

The web build uses `sqlite-wasm`/OPFS for persistence, which requires `SharedArrayBuffer` and therefore **cross-origin isolation**. A plain static file server is not enough unless it also sends the required headers and serves the app in a secure context.

- **Secure context:** serve over **HTTPS** (or `http://localhost` for local testing). Cross-origin isolation is not enabled on insecure origins, so persistence will not work over plain `http://`.
- **Required response headers:** the production host must send
  - `Cross-Origin-Opener-Policy: same-origin`
  - `Cross-Origin-Embedder-Policy: require-corp`
  (The webpack dev server sets these; production servers do not by default.) Set them for the app’s responses (easiest: for all files in the deployed directory).
- **MIME types:** serve `.wasm` as `application/wasm` (and JS/HTML with standard types). Most servers do this by default, but confirm it.
- **Static hosting:** upload/extract the contents of `productionExecutable/` into a directory and serve them with their relative structure intact (the app loads assets relative to `index.html`). Compression (gzip/brotli) for `.js`/`.wasm` is recommended; `file://` URLs are not supported.

**Apache example** (e.g. in `.htaccess` in the deployed directory, with `mod_headers`/`mod_mime` enabled and overrides allowed, such as `AllowOverride FileInfo`):

```apache
<IfModule mod_headers.c>
  Header always set Cross-Origin-Opener-Policy "same-origin"
  Header always set Cross-Origin-Embedder-Policy "require-corp"
</IfModule>

<IfModule mod_mime.c>
  AddType application/wasm .wasm
</IfModule>

# Optional: compress large JS/WASM payloads
<IfModule mod_deflate.c>
  AddOutputFilterByType DEFLATE text/html text/css text/plain \
    text/javascript application/javascript application/wasm application/json
</IfModule>
```

For other servers/CDNs, configure the equivalent COOP/COEP headers and WASM MIME type for the deployed path.

**Verify:** load the site over HTTPS and check in the browser console that `globalThis.crossOriginIsolated === true`, then confirm data persists across reloads.

**Browser/runtime notes:** remote LLM calls from a browser-hosted build are subject to CORS (and related browser constraints such as Private Network Access/mixed content), so arbitrary/user-supplied endpoints may require a CORS-enabled endpoint or proxy; the offline engine and local persistence run client-side. `COEP: require-corp` also means any cross-origin subresources must be served in a CORP/CORS-compatible way (the app’s own bundled assets are same-origin).

Browser tests run via `./gradlew :shared:wasmJsBrowserTest`; they require a Chromium binary, wired from the `CHROME_EXECUTABLE`/`CHROME_BIN` environment variable (the test disables itself when absent).

### Desktop

The desktop target is enabled: `shared/` exposes a `desktop` (JVM) target, and the `desktopApp/` module is the desktop entry point.

```bash
./gradlew :desktopApp:run            # run the desktop app
./gradlew :desktopApp:build          # build the desktop app
# create a distributable app/installer:
./gradlew :desktopApp:createDistributable
```

### iOS

The iOS target is enabled: `shared/` compiles for `iosArm64` (device) and `iosSimulatorArm64` (simulator), packaged as a static `Shared` XCFramework. The `iosApp/` directory holds the Xcode project entry point.

Build and run through Xcode (`iosApp/iosApp.xcodeproj`); Gradle provides the Kotlin/Native tasks, e.g.:

```bash
./gradlew :shared:iosSimulatorArm64Test   # run iOS simulator tests
./gradlew :shared:embedAndSignAppleFrameworkForXcode   # invoked by the Xcode build to embed the framework
```

### All Targets

```bash
./gradlew build   # assemble and test all enabled targets
./gradlew check   # run all verification tasks (tests + lint)
```

## Design Documentation

See [PRD.md](docs/PRD.md) for product requirements.
See [ENG-DESIGN.md](docs/ENG-DESIGN.md) for architecture, data model, LLM interface, and implementation details.

## Implementation Progress

See [IMPLEMENTATION-PLAN.md](docs/IMPLEMENTATION-PLAN.md) for the project roadmap and status.