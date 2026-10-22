# Third-party components

DSH Pocket is an independent Android adaptation, not an official DeepSeek application.

- DeepSeek Harness, fixed commit in `vendor/harness/UPSTREAM.json`: MIT. Source and license included.
- Cordis, Cosmokit, Schemastery: MIT; vendored upstream copies retain their licenses.
- nodejs-mobile 18.20.4 / Node.js and V8: Node distribution license and third-party notices in `app/src/main/assets/licenses/Node-LICENSE.txt`.
- Zod 4.4.3: MIT, license included in assets.
- NanoHTTPD 2.3.1: BSD-3-Clause.
- OkHttp 4.12.0, Okio, Kotlin standard library: Apache-2.0.
- jsoup 1.18.3: MIT.
- PDFBox Android 2.0.27.0 / Apache PDFBox: Apache-2.0.
- Android NDK libc++: Apache-2.0 with LLVM exceptions.

Build-only tooling and versions are recorded in `scripts/package-lock.json`. Dependencies retain their upstream copyrights. Android platform APIs are supplied by the user's operating system.

- Turndown and @joplin/turndown-plugin-gfm: MIT; @mixmark-io/domino: BSD-2-Clause. Runtime licenses included in assets.

UI interaction reference: https://github.com/rikkahub/rikkahub (AGPL-3.0). No RikkaHub code or assets are included; this app retains its own Android UI implementation.

## Launcher artwork
Android launcher vector is adapted from DeepSeek Harness `apps/web/public/favicon.svg`,
at the upstream revision recorded in `vendor/harness/UPSTREAM.json`.
The DeepSeek/DSH name and whale identity belong to their respective owners; this is an unofficial Android adaptation.
