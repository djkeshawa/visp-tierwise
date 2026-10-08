// Loads every JSON Schema of spec/schemas into one ajv 2020 instance, so that each $ref by $id resolves.
import Ajv2020 from 'ajv/dist/2020.js';
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { parseJsonStrict } from './jcs.mjs';

// The $id base of every schema (spec/schemas/README.md).
export const SCHEMA_BASE = 'https://tierwise.github.io/schemas/0.1/';

// Compiles the schemas in `dir` and returns helpers to fetch validators and to validate data.
// Ajv runs in strict mode, so unknown keywords and bad $refs are fatal. Three lint-style checks are relaxed,
// and their notes are counted rather than failed:
//  - strictTypes is logged: valid idioms such as union types or if/then branches without a type are flagged;
//  - strictRequired is off: a required name that a sibling branch defines is valid JSON Schema;
//  - formats are not checked, because ajv-formats is not a dependency; the schemas' patterns carry the checks.
export function createSchemas(dir) {
  const notes = [];
  const logger = { log: () => {}, warn: (message) => notes.push(message), error: (message) => notes.push(message) };
  const ajv = new Ajv2020({
    strict: true,
    strictTypes: 'log',
    strictRequired: false,
    validateFormats: false,
    allErrors: true,
    logger,
  });
  const files = readdirSync(dir).filter((name) => name.endsWith('.json')).sort();
  for (const file of files) {
    const schema = parseJsonStrict(readFileSync(join(dir, file), 'utf8'));
    if (schema.$id !== SCHEMA_BASE + file) throw new Error(`${file} must have the $id ${SCHEMA_BASE}${file}`);
    ajv.addSchema(schema);
  }
  return {
    files,
    // Strict-mode notes that were logged rather than thrown. Counted, not failures.
    notes,
    // Returns the validator of one schema, or of a part of it such as '#/$defs/entries'. Throws if it does not compile.
    validator(file, pointer = '') {
      return ajv.getSchema(SCHEMA_BASE + file + pointer);
    },
    // Returns the list of problems that data has against a validator. An empty list means valid.
    problems(validate, data) {
      if (validate(data)) return [];
      // Name the member that an additionalProperties or required error is about, so the message points at it.
      return validate.errors.map((error) => {
        const member = error.params?.additionalProperty ?? error.params?.missingProperty;
        return `${error.instancePath || '(root)'} ${error.message}${member === undefined ? '' : ` (${member})`}`;
      });
    },
  };
}
