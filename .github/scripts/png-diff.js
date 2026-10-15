// SPDX-License-Identifier: AGPL-3.0-only
// Write a highlighted diff PNG for every snapshot that exists in both the
// before and the after directory (pixelmatch: changed pixels in one color over
// a faded copy of the after image). Differently sized pairs are skipped.
//
// Usage: node png-diff.js <before-dir> <after-dir> <out-dir> <name>...
// Needs `pixelmatch@5` and `pngjs` resolvable from NODE_PATH.
const fs = require('fs');
const path = require('path');
const { PNG } = require('pngjs');
const pixelmatch = require('pixelmatch');

const [beforeDir, afterDir, outDir, ...names] = process.argv.slice(2);
fs.mkdirSync(outDir, { recursive: true });
for (const name of names) {
  const beforeFile = path.join(beforeDir, name);
  if (!fs.existsSync(beforeFile)) continue;
  const before = PNG.sync.read(fs.readFileSync(beforeFile));
  const after = PNG.sync.read(fs.readFileSync(path.join(afterDir, name)));
  if (before.width !== after.width || before.height !== after.height) {
    console.log(`skip ${name}: size changed`);
    continue;
  }
  const diff = new PNG({ width: after.width, height: after.height });
  const pixels = pixelmatch(before.data, after.data, diff.data, after.width, after.height, {
    threshold: 0.1,
    diffColor: [255, 0, 0],
  });
  fs.writeFileSync(path.join(outDir, name), PNG.sync.write(diff));
  console.log(`diff ${name}: ${pixels} px`);
}
