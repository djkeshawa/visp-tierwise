// Reads the clause definitions of spec/sections and the code catalogue of spec/error-codes.md.
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

// A clause definition: a bold ID at the start of a paragraph or list item (AUTHORING.md).
// The OPEN marker counts only when it directly follows the ID, not when a clause merely mentions it.
const CLAUSE_LINE = /^(?:[-*]\s+|>\s*)?\*\*\[([A-Z]+-[1-9][0-9]*)\]\*\*(\s+\*\(OPEN\)\*)?/;

// Reads every section and returns the clauses in reading order: { id, file, line, open }.
export function readClauses(sectionsDir) {
  const clauses = new Map();
  const files = readdirSync(sectionsDir).filter((name) => name.endsWith('.md')).sort();
  for (const file of files) {
    const lines = readFileSync(join(sectionsDir, file), 'utf8').split('\n');
    lines.forEach((text, index) => {
      const match = text.match(CLAUSE_LINE);
      if (!match) return;
      const id = match[1];
      // A repeated ID is kept under its first place, and the tool reports it as a problem.
      if (!clauses.has(id)) clauses.set(id, { id, file: `spec/sections/${file}`, line: index + 1, open: Boolean(match[2]) });
      else clauses.get(id).duplicate = true;
    });
  }
  return clauses;
}

// Splits error-codes.md into entries: { heading, section, cls }. `cls` is the text of the **Class:** line.
function readCatalogueEntries(text) {
  const entries = [];
  let section = '';
  let current = null;
  for (const line of text.split('\n')) {
    if (line.startsWith('## ')) {
      section = line.slice(3).trim();
      current = null;
    } else if (line.startsWith('### ')) {
      current = { heading: line.slice(4).trim(), section, cls: null };
      entries.push(current);
    } else if (current && current.cls === null && line.startsWith('**Class:**')) {
      current.cls = line.slice('**Class:**'.length).trim();
    }
  }
  return entries;
}

// Returns the codes of the catalogue by group, and any catalogue problem found while reading it.
// engine: the engine group (requirements 6.1), evaluation: codes whose class names evaluation (6.2),
// lint: the lint rule identifiers of section "Lint rules" other than the naming/* family, tooling: the tooling group.
export function readCatalogue(text) {
  const entries = readCatalogueEntries(text);
  const catalogue = { engine: [], evaluation: [], lint: [], tooling: [], problems: [] };
  for (const entry of entries) {
    if (entry.section === 'Lint rules') {
      if (!entry.heading.startsWith('naming/')) catalogue.lint.push(entry.heading);
      continue;
    }
    if (entry.cls === null) {
      catalogue.problems.push(`${entry.heading} has no **Class:** line`);
      continue;
    }
    if (entry.section === 'Engine errors') {
      if (!entry.cls.startsWith('engine')) catalogue.problems.push(`${entry.heading} is under Engine errors but its class is "${entry.cls}"`);
      catalogue.engine.push(entry.heading);
    } else if (entry.section === 'Tooling error') {
      catalogue.tooling.push(entry.heading);
    }
    if (entry.cls.includes('evaluation')) catalogue.evaluation.push(entry.heading);
  }
  return catalogue;
}
