const DAY = 86_400_000;
const DAYS = ["SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"];

export function planningZone(value: unknown): string {
  if (typeof value !== "string" || value.length > 80) throw new Error("Invalid timezone");
  return new Intl.DateTimeFormat("en", {timeZone: value}).resolvedOptions().timeZone;
}

function localDate(time: number, zone: string): string {
  const parts = new Intl.DateTimeFormat("en", {
    timeZone: zone, year: "numeric", month: "2-digit", day: "2-digit",
  }).formatToParts(time);
  const part = (type: string) => parts.find((p) => p.type === type)!.value;
  return `${part("year")}-${part("month")}-${part("day")}`;
}

// Find the first instant of a local date. This handles DST midnight gaps and
// repeated hours without assuming that a calendar day always lasts 24 hours.
function startOfDate(date: string, zone: string): number {
  const utc = Date.parse(`${date}T00:00:00Z`);
  let low = utc - 2 * DAY;
  let high = utc + 2 * DAY;
  while (low < high) {
    const mid = Math.floor((low + high) / 2);
    if (localDate(mid, zone) < date) low = mid + 1;
    else high = mid;
  }
  return low;
}

export function planningWindow(now: number, zone: string, startDay: unknown) {
  const timeZone = planningZone(zone);
  const today = Date.parse(`${localDate(now, timeZone)}T00:00:00Z`);
  const configured = DAYS.indexOf(String(startDay));
  const weekday = configured < 0 ? 1 : configured;
  const current = today - ((new Date(today).getUTCDay() - weekday + 7) % 7) * DAY;
  const iso = (time: number) => new Date(time).toISOString().slice(0, 10);
  return {
    timeZone,
    startDay: DAYS[weekday],
    currentWeekStart: iso(current),
    weekStart: iso(current + 7 * DAY),
    opensAtMillis: startOfDate(iso(current), timeZone),
    appliesAtMillis: startOfDate(iso(current + 7 * DAY), timeZone),
    endsAtMillis: startOfDate(iso(current + 14 * DAY), timeZone),
  };
}

export function targetForPlan(current: number, choice: unknown, custom: unknown): number {
  const target = choice === "repeat" ? current : choice === "gentler" ?
    Math.max(10_000, Math.round(current * 0.8 / 10_000) * 10_000) :
    choice === "custom" ? custom : null;
  if (!Number.isInteger(target) || (target as number) < 10_000 ||
      (target as number) > 2_800_000) throw new Error("Invalid weekly target");
  return target as number;
}
