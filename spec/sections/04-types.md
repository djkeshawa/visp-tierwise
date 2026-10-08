# 4. Value types and normalisation

This section defines the type declarations used by keys, object fields, facts and object-valued effects. It defines the eight types, the constraints on their declarations and values, and how strings and decimals are normalised and compared. It does not define which merge strategies fit which types (section 5, `KEY` and `MERGE`), how facts and entities are declared (section 3, `ENTITY` and `FACT`), which comparison operators conditions offer (section 8, `RULE`), or which error or source-failure treatment applies to values received at runtime ([FACT-35], [SVC-31], [SVC-34]).

## 4.1 Type declarations

**[TYPE-1]** A type declaration MUST be a map.

**[TYPE-2]** The `type` member of a type declaration MUST be exactly one of the strings `boolean`, `integer`, `decimal`, `string`, `enum`, `set`, `list` or `object`. The comparison is exact under [NORM-6], so type names are case-sensitive.

**[TYPE-3]** A key declaration, an object field declaration and an `items` declaration MUST each have a `type` member.

**[TYPE-4]** A declaration that lacks the `type` member required by [TYPE-3] MUST be reported as `E_TYPE_REQUIRED`.

Fact declarations without `type` use the same code under [FACT-6].

**[TYPE-5]** Key declarations, object field declarations, `items` declarations, fact declarations and the fields of a feature's `returns: { object: … }` declaration ([FEAT-11]) MUST use the type declaration grammar of this section.

**[TYPE-6]** The `enum` list of a feature's `returns: { enum: … }` declaration ([FEAT-7]) MUST satisfy the rules for `values` in [TYPE-41] to [TYPE-43].

ADR 0010 §6 treats each listed effect as an ordinary enum value, which is why these rules apply.

Which types a fact may declare is decided by [FACT-7]. Which other type members a fact may carry is open under [FACT-9].

In this section, the *type members* of a declaration are `type`, `min`, `max`, `format`, `values`, `universe`, `items` and `fields`. They sit alongside the declaration's own members, which the owning section defines: [KEY-2] and [KEY-3] for keys, section 3 (`FACT`) for facts, and [TYPE-8] for object fields.

**[TYPE-7]** A declaration MUST NOT contain a type member that its `type` does not define. The type members that each type defines, besides `type`, are:

- `integer`: `min` and `max`;
- `string`: `format`;
- `enum`: `values`;
- `set`: `universe`;
- `list`: `items`;
- `object`: `fields`;
- `boolean` and `decimal`: none.

A type member written on a key or fact declaration is still governed by this clause.

**[TYPE-8]** A type declaration MUST NOT contain `optional` unless it is an object field declaration.

**[TYPE-9]** When `optional` is present, its value MUST be a boolean.

**[TYPE-10]** A field whose declaration has no `optional` member, or has `optional` set to `false`, MUST be treated as required.

**[TYPE-11]** *(OPEN)* Which code reports a malformed type declaration? The cases are: a declaration that is not a map; a `type` that is not a string, is `null`, or names an unknown type; an unknown `format`; a type member its type does not define ([TYPE-7]); a missing `values`, `universe`, `items` or `fields` ([TYPE-40], [TYPE-53], [TYPE-59]); a member of an object field or `items` declaration that is neither a type member nor `optional`; an `optional` outside an object field declaration ([TYPE-8]); an `optional` that is not a boolean ([TYPE-9]); a `min` or `max` that is not an integer or is `null`; a `values` or `universe` that is not a list, or that has an entry that is not a string; and a `fields` that is not a map. Section 6.1 names no code for these. The candidate is a new declaration code, `E_TYPE_DECLARATION`.

## 4.2 Booleans, integers and decimals

**[TYPE-12]** A `boolean` value MUST be the boolean `true` or `false`. No other value is a boolean value.

**[TYPE-13]** The `min` and `max` members of an `integer` declaration, where present, MUST each be a source integer ([TYPE-15]) within ±(2^53 − 1).

**[TYPE-14]** When both `min` and `max` are present, `min` MUST NOT be greater than `max`.

**[TYPE-15]** In a source, an `integer` value MUST be a plain scalar that [SRC-1] reads as the core-schema integer type.

**[TYPE-16]** The value of such a scalar is the integer that its token denotes, so `-0` denotes 0.

**Example (non-normative).** `5`, `+5`, `0x1F` and `0o17` are integer values. `5.0`, `1e3` and `"5"` are not; each fails validation under [TYPE-70].

**[TYPE-17]** *(OPEN)* How does an evaluator decide that a runtime number is an integer value? Some runtimes hold every number as a floating-point value, so they cannot tell `5` from `5.0`. Recommended: a runtime number is an integer value when its mathematical value is an integer, and conformance vectors use integer tokens only.

**[TYPE-18]** Every `integer` value MUST lie within ±(2^53 − 1), whether or not its declaration gives bounds.

**[TYPE-19]** When its declaration has `min` or `max`, an `integer` value MUST lie within the declared bounds, which are inclusive.

**[TYPE-20]** Subject to [TYPE-71], an `integer` source value outside ±(2^53 − 1) MUST be reported as `E_INTEGER_RANGE`.

**[TYPE-21]** Subject to [TYPE-71], an `integer` source value outside its declared bounds MUST be reported as `E_OUT_OF_RANGE`.

> **Implementation note.** A JavaScript number is an IEEE double and loses precision above 2^53. An implementation that holds numbers this way should check the digits of the original token against the range, not the converted number.

For a runtime integer fact value outside this range, [FACT-41] gives the error. Whether a fact declaration may give bounds is open under [FACT-9].

**[TYPE-22]** *(OPEN)* Which codes report (a) a `min` or `max` outside ±(2^53 − 1), which [TYPE-13] requires, (b) a `min` greater than `max`, which [TYPE-14] requires, and (c) an integer value that is both outside ±(2^53 − 1) and outside its declared bounds, where [TYPE-20] and [TYPE-21] would both apply? Section 6.1 names no code for (a) or (b). `E_INTEGER_RANGE` is the candidate for (a). For (c), the number of findings is decided by [TYPE-23].

**[TYPE-23]** *(OPEN)* Which findings does a compiler report when one source value breaks more than one rule? Examples are an object with an unknown field and a missing required field, a set with a repeated member and a member outside `universe`, and a list with several invalid elements. Recommended: the compiler reports each violation as its own finding, and the order of findings is left to the `FIND` area ([FIND-26]).

**[TYPE-24]** A `decimal` value MUST be a string that matches `[+-]?[0-9]+(\.[0-9]+)?` in full, where `[0-9]` stands for an ASCII digit.

**[TYPE-25]** A number, whether written in a source or supplied at runtime, MUST NOT be a decimal value.

A finding for a number written where a decimal is expected typically suggests quoting it, because a YAML number may already have lost precision (ADR 0011 §3).

**[TYPE-26]** A decimal value MUST be compared in canonical form (see [NORM-9] and [NORM-10]).

**[TYPE-27]** A decimal value MUST be compared by equality and membership only. No ordering comparison is defined for decimals. The operators that conditions offer are defined by section 8 (see [RULE-15] and [RULE-37]).

## 4.3 Strings and formats

**[TYPE-28]** A `string` value MUST be a string.

**[TYPE-29]** A `string` declaration without `format` MUST accept every string, subject to [NORM-8].

**[TYPE-30]** When `format` is absent, the empty string is a valid value.

**[TYPE-31]** A `format` member MUST be `time_hhmm` or `iana_tz`. Any other value is a malformed declaration under [TYPE-11].

**[TYPE-32]** When `format` is present, a `string` value MUST satisfy the rule for that format: [TYPE-33] for `time_hhmm`, and for `iana_tz`, [TYPE-34] at compile time and [TYPE-37] at runtime.

**[TYPE-33]** A `time_hhmm` value MUST match `^([01][0-9]|2[0-3]):[0-5][0-9]$` in full, at compile time and at runtime.

> **Implementation note.** Match the whole input. Java's `$` also matches before a final line terminator, and JavaScript's `$` without the multiline flag does not. Use ASCII classes, because `\d` matches other digits in some regular expression engines.

**[TYPE-34]** A compiler MUST accept an `iana_tz` value only when it equals, code point for code point, a zone or link name listed in `spec/data/tz-names.txt`. That file lists the zone and link names of the IANA tz database release pinned by the spec version ([TYPE-36]).

**[TYPE-35]** Any other `iana_tz` source value MUST fail validation under [TYPE-70].

**[TYPE-36]** *(OPEN)* Which IANA tz database release is pinned, and what is the format of `spec/data/tz-names.txt`? The file does not exist yet. Recommended: a constant in `spec/spec.md` names the release, and the file is UTF-8 with one zone or link name per line, LF line endings, sorted by code point, and no comments or blank lines.

**[TYPE-37]** An evaluator MUST check an `iana_tz` value received at runtime against the syntax `^[A-Za-z0-9_+-]+(/[A-Za-z0-9_+-]+)*$` in full, and nothing more.

**[TYPE-38]** An evaluator MUST NOT consult time-zone data or convert time zones.

**[TYPE-39]** A runtime `iana_tz` value that fails the syntax of [TYPE-37] MUST fail validation under [TYPE-70].

## 4.4 Enumerations and sets

**[TYPE-40]** An `enum` declaration MUST contain `values`, and a `set` declaration MUST contain `universe`.

**[TYPE-41]** The `values` of an `enum` declaration and the `universe` of a `set` declaration MUST each be a list of strings.

**[TYPE-42]** The `values` of an `enum` declaration and the `universe` of a `set` declaration MUST each be non-empty.

**[TYPE-43]** The `values` of an `enum` declaration and the `universe` of a `set` declaration MUST NOT repeat a string.

**[TYPE-44]** An `enum` value MUST be equal, under [NORM-6], to one entry of `values`.

**[TYPE-45]** A `set` value MUST be a list of strings.

**[TYPE-46]** A `set` value MUST NOT repeat a member.

**[TYPE-47]** Every member of a `set` value MUST be an entry of `universe`, compared under [NORM-6].

**[TYPE-48]** Subject to [TYPE-71], a member of a `set` source value that is not an entry of `universe` MUST be reported as `E_NOT_IN_UNIVERSE`.

**[TYPE-49]** The empty list is a valid set value.

**[TYPE-50]** A compiler MUST store the members of each set value in the declaration order of `universe`, whatever order the source lists them in.

**[TYPE-51]** Every set value that an evaluator produces as a result MUST list its members in the declaration order of `universe`.

**[TYPE-52]** *(OPEN)* Which codes report (a) an empty `values` or `universe` list ([TYPE-42]), (b) a repeated string in a `values` or `universe` declaration ([TYPE-43]), and (c) a repeated member in a set value ([TYPE-46])? The candidate for (b) and (c) is `E_DUPLICATE_VALUE`. Section 6.1 names no code for (a).

## 4.5 Lists and objects

**[TYPE-53]** A `list` declaration MUST contain `items`.

**[TYPE-54]** The `items` member of a `list` declaration MUST be a type declaration of any type (nesting is open under [TYPE-69]).

Which strategies a `list` key may use is decided by [MERGE-3].

**[TYPE-55]** A `list` value MUST be an ordered sequence.

**[TYPE-56]** Every element of a `list` value MUST be a valid value of the declared `items` type.

**[TYPE-57]** The empty sequence is a valid `list` value.

**[TYPE-58]** Repeated elements of a `list` value are valid.

**[TYPE-59]** An `object` declaration MUST contain `fields`, a map from field name to type declaration.

**[TYPE-60]** Each entry of `fields` MUST be an object field declaration: a type declaration under this section ([TYPE-5]) that MAY also contain `optional` ([TYPE-8] to [TYPE-10]).

**[TYPE-61]** An `object` value MUST be a map.

**[TYPE-62]** Every field not declared `optional` MUST be present in an object value, except in a value that [MERGE-11] permits to be partial and in the object values nested within it ([MERGE-12]).

**[TYPE-63]** *(OPEN)* Which code reports a source object value that lacks a field not declared `optional`: `E_TYPE_REQUIRED` or `E_TYPE_MISMATCH` through [TYPE-71]? Recommended: `E_TYPE_MISMATCH`, because the value does not match its object type. ADR 0011 §1 uses `E_TYPE_REQUIRED` only for a declaration with no `type`.

**[TYPE-64]** An `object` value MUST NOT contain a key that names no declared field, and such a key MUST NOT be ignored.

**[TYPE-65]** Every present field of an `object` value MUST have a valid value of its declared type.

**[TYPE-66]** An absent `optional` field has no value in the object.

**[TYPE-67]** A present field whose value is `null` MUST be handled under [TYPE-77], whether or not the field is declared `optional`.

**[TYPE-68]** *(OPEN)* Which strings are valid field names, and may an `object` declare zero fields? Duplicate map keys are rejected by [SRC-3]. Naming patterns are project policy and are not part of this section.

**[TYPE-69]** *(OPEN)* May `list` and `object` declarations nest to any depth? The sources set no limit, and no nesting error code exists.

## 4.6 Null and general validation

**[TYPE-70]** A value that does not match its declared type MUST fail validation.

**[TYPE-71]** When the value comes from a source, a failure of validation MUST be reported with the first of these codes that applies: (1) the code that the clause owning the value names for an invalid value, such as `E_INVALID_SETTING` for setting values ([POL-17]) and `E_ILLEGAL_FACT_VALUE` for condition literals ([RULE-18]); (2) the code that this section names for that failure ([TYPE-20], [TYPE-21], [TYPE-48]); (3) `E_TYPE_MISMATCH`.

**[TYPE-72]** An effect that fails validation MUST be reported as [FEAT-14] requires.

**[TYPE-73]** A value received at runtime MUST NOT be reported with any code from this section.

**[TYPE-74]** A fact value received at runtime that fails validation MUST be handled as [FACT-35] defines.

**[TYPE-75]** A provider value that fails validation MUST be handled as [SVC-31] and [SVC-34] define.

**[TYPE-76]** `null` MUST NOT be a valid value of any type, including an `optional` object field.

**[TYPE-77]** A `null` given where a value of a declared type is expected MUST be handled under [TYPE-70], unless the owning clause gives `null` a meaning.

**[TYPE-78]** A meaning that an owning clause gives to `null` MUST NOT make `null` a value of the type.

The clauses that give `null` a meaning are [FACT-16] and [FACT-19] for missing facts, [MERGE-6] for the absent results of `min` and `max`, and [FEAT-9] for `nullable` effects. [SVC-35] applies [TYPE-76] to a provider entry whose value is `null`.

## 4.7 Normalisation and comparison

**[NORM-1]** A compiler MUST NFC-normalise each of the following strings that a source supplies: string values; `enum` and `set` values; entries of an `enum` `values` list and of a `set` `universe` list; entries of a feature's `returns` `enum` list ([FEAT-7]); field names in a `fields` declaration; and keys of an object value.

**[NORM-2]** Normalisation MUST happen before any format, membership, repetition or comparison check.

Identifiers such as keys, entities, facts and node IDs are outside [NORM-1] and are normalised by the sections that declare them.

**[NORM-3]** An evaluator MUST NOT apply Unicode normalisation (NFC or any other form) to any string it receives, including the keys of object values.

**[NORM-4]** An evaluator MUST compare each string it receives as received, under [NORM-6].

**[NORM-5]** A string that is not in NFC does not equal the NFC string that it would normalise to.

**[NORM-6]** Two strings are equal if and only if they contain the same sequence of Unicode code points.

**[NORM-7]** String comparison is case-sensitive.

> **Implementation note.** For well-formed strings, equality is the same whether strings are compared by UTF-16 code unit or by code point. This holds for equality only, not for ordering ([DET-5]).

**[NORM-8]** *(OPEN)* Is a string that contains an unpaired surrogate valid? Some runtimes can hold such values. The sources do not say whether they are rejected at compile time, or compared as received at runtime. Until this is settled, [NORM-6] is undefined for such strings. [BUNDLE-13] already excludes them from bundles.

**[NORM-9]** A canonical decimal string is one that matches `-?(0|[1-9][0-9]*)(\.[0-9]*[1-9])?` in full and is not `-0`, where `[0-9]` and `[1-9]` stand for ASCII digits. Zero is written `0`.

**[NORM-10]** To canonicalise a string `S` that matches `[+-]?[0-9]+(\.[0-9]+)?` in full ([TYPE-24]), an implementation MUST apply these steps:

1. Split `S` into a sign, the integer digits `I` and the fraction digits `F`. The sign is `-` or empty; a `+` is dropped. `F` is empty when `S` has no `.`.
2. Remove leading zeros from `I`. If `I` becomes empty, set `I` to `0`.
3. Remove trailing zeros from `F`.
4. If `F` is empty, the body is `I`. Otherwise the body is `I`, then `.`, then `F`.
5. If the body is `0`, drop the sign. Otherwise keep the sign if `S` had `-`.

**[NORM-11]** The result of [NORM-10] MUST be a canonical decimal string ([NORM-9]) with the same numeric value as `S`.

**[NORM-12]** Two strings with the same numeric value MUST give the same result under [NORM-10].

**[NORM-13]** A compiler MUST canonicalise each source `decimal` value that matches [TYPE-24] with [NORM-10].

**[NORM-14]** A source `decimal` value that does not match [TYPE-24] MUST fail validation under [TYPE-70].

**[NORM-15]** An evaluator MUST canonicalise each runtime `decimal` string that matches [TYPE-24] with [NORM-10] before comparing it.

**[NORM-16]** A runtime `decimal` value that does not match [TYPE-24] MUST be handled under [TYPE-70].

**[NORM-17]** Values MUST NOT coerce between types.

A string is not a number, a number is not a boolean, and a number is not a decimal value. Under the core schema that [SRC-1] uses, words such as `yes`, `no`, `on` and `off` are strings, not booleans.

**[NORM-18]** Values of different declared types MUST NOT be equal. How conditions compare values is defined by [RULE-35] to [RULE-38].

## Summary of codes (non-normative)

| Code | Source value | Clauses |
| --- | --- | --- |
| `E_TYPE_REQUIRED` | Key, object field or `items` declaration with no `type` | [TYPE-4] |
| `E_TYPE_MISMATCH` | Value of the wrong form, a `null`, an unknown object field, an out-of-set `iana_tz` or `enum` value, a number used as a decimal, a `set` value that is not a list | [TYPE-12], [TYPE-15], [TYPE-24], [TYPE-25], [TYPE-35], [TYPE-44], [TYPE-45], [TYPE-64], [TYPE-71], [TYPE-77], [NORM-14] |
| `E_INTEGER_RANGE` | Integer value outside ±(2^53 − 1) | [TYPE-20] |
| `E_OUT_OF_RANGE` | Integer value outside its declared bounds | [TYPE-21] |
| `E_NOT_IN_UNIVERSE` | Set member not in `universe` | [TYPE-48] |

An absent required object field has no code in this table; its code is open under [TYPE-63]. All codes in this table apply to source values only, and a code that the clause owning the value names takes precedence over them ([TYPE-71]). Effects use [FEAT-14]. A runtime value is not reported with these codes ([TYPE-73]); it is handled as [FACT-35], [SVC-31] and [SVC-34] define.

## Examples (non-normative)

**Example (non-normative).** Type declarations:

```yaml
max_lines: { type: integer, min: 1, max: 500 }
fee_rate:  { type: decimal }
regions:   { type: set, universe: [north, south, west] }
window:
  type: object
  fields:
    start: { type: string, format: time_hhmm }
    end:   { type: string, format: time_hhmm, optional: true }
```

**Example (non-normative).** Decimal canonicalisation under [NORM-10]:

| Source string | Canonical form | Result |
| --- | --- | --- |
| `"1.50"` | `"1.5"` | Accepted |
| `"+007.100"` | `"7.1"` | Accepted |
| `"-0.0"` | `"0"` | Accepted |
| `"00.5"` | `"0.5"` | Accepted |
| `"1e3"` | none | `E_TYPE_MISMATCH` |
| `1.5` (a number) | none | `E_TYPE_MISMATCH` |

## Open points

- [TYPE-11]: the code for malformed type declarations, such as an unknown type name or a member its type does not define.
- [TYPE-17]: how an evaluator decides that a runtime number is an integer value.
- [TYPE-22]: the codes for invalid declared bounds, and the findings for an integer that fails both checks.
- [TYPE-23]: the findings for a source value that breaks more than one rule.
- [TYPE-36]: the pinned tz release and the format of `spec/data/tz-names.txt`.
- [TYPE-52]: the codes for an empty `values` or `universe`, a repeated entry, and a repeated set member.
- [TYPE-63]: the code for an absent required object field from a source.
- [TYPE-68]: the syntax of field names, and whether an object may declare zero fields.
- [TYPE-69]: whether list and object declarations may nest to any depth.
- [NORM-8]: whether a string containing an unpaired surrogate is valid.
