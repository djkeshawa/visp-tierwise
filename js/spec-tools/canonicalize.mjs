// Writes the canonical bundle bytes for a content object, and prints the bundleVersion that names them.
// Usage: node spec-tools/canonicalize.mjs <content.json> <out bundle.json>
// The envelope {bundleVersion, content} is written as JCS with no trailing newline (BUNDLE-1 to BUNDLE-10).
// It is checked against bundle.schema.json first, and nothing is written when that check fails.
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { bundleBytes, bundleVersionOf, parseBundleJson } from './lib/jcs.mjs';
import { createSchemas } from './lib/schemas.mjs';

const SPEC_SCHEMAS = fileURLToPath(new URL('../../spec/schemas', import.meta.url));

// Runs the tool and returns the exit code: 0 on success, 1 on a failed check, 2 on bad arguments.
function main([input, output]) {
  if (!input || !output || process.argv.length !== 4) {
    console.error('usage: node spec-tools/canonicalize.mjs <content.json> <out bundle.json>');
    return 2;
  }
  let text;
  try {
    text = readFileSync(input, 'utf8');
  } catch (error) {
    console.error(`${input}: cannot be read: ${error.message}`);
    return 1;
  }
  let content;
  try {
    content = parseBundleJson(text);
  } catch (error) {
    console.error(`${input}: not a content object in bundle form: ${error.message}`);
    return 1;
  }
  if (content === null || typeof content !== 'object' || Array.isArray(content)) {
    console.error(`${input}: the content must be a JSON object`);
    return 1;
  }
  let bundleVersion;
  let bytes;
  try {
    bundleVersion = bundleVersionOf(content);
    bytes = bundleBytes(bundleVersion, content);
  } catch (error) {
    console.error(`${input}: ${error.message}`);
    return 1;
  }
  const schemas = createSchemas(SPEC_SCHEMAS);
  const problems = schemas.problems(schemas.validator('bundle.schema.json'), { bundleVersion, content });
  if (problems.length > 0) {
    for (const problem of problems) console.error(`${input}: ${problem}`);
    console.error('nothing was written: the content does not match bundle.schema.json');
    return 1;
  }
  try {
    writeFileSync(output, bytes);
  } catch (error) {
    console.error(`${output}: cannot be written: ${error.message}`);
    return 1;
  }
  console.log(bundleVersion);
  return 0;
}

process.exitCode = main(process.argv.slice(2));
