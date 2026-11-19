;(function(config) {
  if (config.devServer) {
    config.devServer.headers = [
        { key: 'Cross-Origin-Opener-Policy', value: 'same-origin' },
        { key: 'Cross-Origin-Embedder-Policy', value: 'require-corp' }
    ]
  }

  // Kotlin/Wasm glue and dependencies (skiko, sqlite worker, kotlinx-io) rely on
  // import.meta in ESM; serve the bundle as a module instead of a UMD script.
  config.experiments = config.experiments || {};
  config.experiments.outputModule = true;
  config.output = config.output || {};
  config.output.module = true;
  config.output.chunkFormat = 'module';
  delete config.output.library;
  delete config.output.libraryTarget;
  delete config.output.globalObject;

  // Eval-based devtools emit code incompatible with ESM output/module parsing.
  if (Array.isArray(config.plugins)) {
    config.plugins = config.plugins.filter(function(plugin) {
      return !(plugin && plugin.constructor && plugin.constructor.name === 'EvalSourceMapDevToolPlugin');
    });
  }
  config.devtool = 'source-map';

  // Generated Kotlin/Wasm import-object glue may include Node-only helpers that
  // access `import.meta` directly (`const importMeta = import.meta;`), which is
  // illegal in non-module script bundles and fragile across bundling modes.
  // Rewrite that specific pattern to a browser-safe value before bundling.
  try {
    const fs = require('fs');
    const path = require('path');
    const loaderPath = path.resolve(__dirname, 'patch-import-meta-loader.js');
    if (!fs.existsSync(loaderPath)) {
      fs.writeFileSync(
        loaderPath,
        "module.exports = function(source) {\n" +
        "  return source.replace(\n" +
        "    /const importMeta = import\\.meta;/g,\n" +
        "    \"const importMeta = (typeof document !== 'undefined' ? { url: document.baseURI } : { url: '' });\"\n" +
        "  );\n" +
        "};\n"
      );
    }
    config.module.rules = config.module.rules || [];
    config.module.rules.push({
      test: /alphathinker-webApp\.import-object\.mjs$/,
      enforce: 'pre',
      use: [{ loader: loaderPath }]
    });
  } catch (e) {
    // Best-effort; if the loader can't be installed, bundling may still work
    // when served as a true ES module.
  }
})(config);