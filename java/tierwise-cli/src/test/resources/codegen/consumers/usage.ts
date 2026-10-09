// Type checks of the generated rich module. The codegen tests copy this file next to tierwise.ts and run
// tsc on both. A line marked @ts-expect-error must fail to compile, so the marker proves the type rule.
import { TypedEvaluator } from "./tierwise.js";
import type { Evaluator } from "@tierwise/evaluator";

declare const evaluator: Evaluator;

export async function check(): Promise<void> {
  const typed = new TypedEvaluator(evaluator);

  const fee: string = typed.resolveSync(undefined, "rich.fee.rate").value;
  const tier: "BASIC" | "PRO" = (await typed.resolve(undefined, "rich.tier.name")).value;
  const limit: number | null = typed.resolveSync(undefined, "rich.limit.count").value;
  const flag: boolean = typed.resolveSync(undefined, "rich.flag.enabled").value;
  const windows: ReadonlyArray<{ readonly start: number; readonly note?: string }> =
    typed.resolveSync(undefined, "rich.rules.windows").value;
  const regions: ReadonlyArray<"EAST" | "WEST"> = typed.resolveSync(undefined, "rich.regions.allowed").value;

  // @ts-expect-error a max key may be absent, so its value is not a plain number
  const notAbsent: number = typed.resolveSync(undefined, "rich.limit.count").value;
  // @ts-expect-error an unknown key name is rejected
  typed.resolveSync(undefined, "rich.no.such.key");
  // @ts-expect-error a key's value is not the wrong type
  const wrongTier: "PRO" = typed.resolveSync(undefined, "rich.tier.name").value;

  const gate = await typed.decide(undefined, "rich.gate.check", { facts: { "order.paid": true } });
  // @ts-expect-error a nullable enum effect includes null
  const gateNotNull: "ALLOW" | "DENY" = gate.effect;
  const gateKnown: "ALLOW" | "DENY" | null = gate.effect;

  const quote = typed.decideSync(undefined, "rich.quote.shape");
  const days: number = quote.effect.days;
  // @ts-expect-error an object effect's enum field has only its declared members
  const method: "NEXT_DAY" = quote.effect.method;
  // @ts-expect-error an unknown feature name is rejected
  typed.decideSync(undefined, "rich.no.such.feature");

  console.log(fee, tier, limit, flag, windows, regions, notAbsent, wrongTier, gateNotNull, gateKnown, days, method);
}
