import { build } from "/opt/dsh-android/build-tools/node_modules/esbuild/lib/main.js";
import fs from "node:fs";
import path from "node:path";
const root = path.resolve(
  process.env.HARNESS_SOURCE || "vendor/harness",
);
const packages = new Map();
function walk(dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (!e.isDirectory()) continue;
    const d = path.join(dir, e.name);
    if (fs.existsSync(path.join(d, "package.json"))) {
      const p = JSON.parse(
        fs.readFileSync(path.join(d, "package.json"), "utf8"),
      );
      packages.set(p.name, d);
    } else walk(d);
  }
}
walk(root + "/packages");
walk(root + "/vendor");
const result = await build({
  entryPoints: ["runtime/main.ts"],
  outfile: "app/src/main/assets/runtime/main.cjs",
  bundle: true,
  platform: "node",
  target: "node18",
  format: "cjs",
  metafile: true,
  nodePaths: ["/opt/dsh-android/build-tools/node_modules"],
  banner: {
    js: 'const __bundleUrl = require("node:url").pathToFileURL(__filename).href;',
  },
  define: { "import.meta.url": "__bundleUrl" },
  plugins: [
    {
      name: "upstream-source",
      setup(b) {
        b.onResolve({ filter: /^\.\/transport\.ts$/ }, a => {
          if (a.importer.includes('/web-deepseek-api/')) return { path: path.resolve('runtime/web-transport.ts') };
        });
        b.onResolve({ filter: /^@deepseek-ai\// }, (a) => {
          const bits = a.path.split("/");
          const pkg = bits.slice(0, 2).join("/");
          const dir = packages.get(pkg);
          if (!dir) return;
          const sub = bits.slice(2).join("/");
          let f =
            sub === "package.json"
              ? dir + "/package.json"
              : dir + "/src/" + (sub ? sub.replace(/^src\//, "") : "index");
          if (!path.extname(f)) f += ".ts";
          return { path: f };
        });
        b.onLoad({ filter: /\/llm\/src\/attribution.ts$/ }, (a) => ({
          contents: fs
            .readFileSync(a.path, "utf8")
            .replace(
              "createRequire(import.meta.url)('../package.json')",
              JSON.stringify({ version: "0.2.1-rc.2" }),
            ),
          loader: "ts",
        }));
      },
    },
  ],
});
fs.mkdirSync("build", { recursive: true });
fs.writeFileSync(
  "build/runtime-meta.json",
  JSON.stringify(result.metafile, null, 2),
);
console.log(
  "Bundled upstream core:",
  Object.keys(result.metafile.inputs).length,
  "modules",
);
// nodejs-mobile is built without ICU; expand Unicode property regexes at build time.
const babel =
  await import("/opt/dsh-android/build-tools/node_modules/@babel/core/lib/index.js");
const regexPlugin = (
  await import("/opt/dsh-android/build-tools/node_modules/@babel/plugin-transform-unicode-property-regex/lib/index.js")
).default;
const setsPlugin = (
  await import("/opt/dsh-android/build-tools/node_modules/@babel/plugin-transform-unicode-sets-regex/lib/index.js")
).default;
const output = "app/src/main/assets/runtime/main.cjs";
const rewritePattern = (
  await import("/opt/dsh-android/build-tools/node_modules/regexpu-core/rewrite-pattern.js")
).default;
const constructors = () => ({
  visitor: {
    NewExpression(p) {
      const n = p.node;
      if (
        n.callee.name === "RegExp" &&
        n.arguments[0]?.type === "StringLiteral" &&
        n.arguments[0].value.includes("\\p{")
      ) {
        const flags = n.arguments[1]?.value || "";
        n.arguments[0].value = rewritePattern(n.arguments[0].value, flags, {
          unicodePropertyEscapes: "transform",
          unicodeSetsFlag: "transform",
        });
        if (n.arguments[1]) n.arguments[1].value = flags.replace("v", "u");
      }
    },
  },
});
const transformed = babel.transformSync(fs.readFileSync(output, "utf8"), {
  configFile: false,
  babelrc: false,
  plugins: [constructors, regexPlugin, setsPlugin],
  compact: false,
});
fs.writeFileSync(output, transformed.code + "\n");
