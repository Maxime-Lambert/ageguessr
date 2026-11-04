/**
 * The reasons of a player's report (PhotoReportReason, HintReportReason in ageguessr-api),
 * shared by the game, which sends them, and the two admin queues, which count them.
 */
export type PhotoReportReason =
  'TOO_SMALL' | 'NOT_IDENTIFIABLE' | 'WRONG_PERSON' | 'LOOKS_MINOR' | 'DISTURBING_PERSON' | 'OTHER';
export type HintReportReason = 'WRONG_YEAR' | 'FALSE' | 'UNCLEAR' | 'DISTURBING' | 'OTHER';

/** "Wrong person (2), Other (1)": the reasons given, in the order of the labels. */
export function reasonsText<R extends string>(
  labels: Record<R, string>,
  reasons: Partial<Record<R, number>>,
): string {
  return (Object.keys(labels) as R[])
    .filter((reason) => reasons[reason])
    .map((reason) => `${labels[reason]} (${reasons[reason]})`)
    .join(', ');
}
