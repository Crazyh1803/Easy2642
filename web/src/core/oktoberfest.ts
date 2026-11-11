// Dates of the Munich Oktoberfest, used for the once-a-year tip reminder. Dates are local.
const SATURDAY = 6;
const SUNDAY = 0;

/** Opening day: the first Saturday on or after 16 September. */
export function oktoberfestStart(year: number): Date {
  const d = new Date(year, 8, 16);
  while (d.getDay() !== SATURDAY) d.setDate(d.getDate() + 1);
  return d;
}
/** Last day: the first Sunday in October. */
export function oktoberfestEnd(year: number): Date {
  const d = new Date(year, 9, 1);
  while (d.getDay() !== SUNDAY) d.setDate(d.getDate() + 1);
  return d;
}
export function oktoberfestIsOn(date: Date): boolean {
  const day = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
  return day >= oktoberfestStart(date.getFullYear()).getTime() && day <= oktoberfestEnd(date.getFullYear()).getTime();
}
/** True at most once per calendar year, and only while the festival is on. */
export const shouldPromptForTip = (today: Date, lastPromptYear: number): boolean =>
  oktoberfestIsOn(today) && lastPromptYear !== today.getFullYear();
