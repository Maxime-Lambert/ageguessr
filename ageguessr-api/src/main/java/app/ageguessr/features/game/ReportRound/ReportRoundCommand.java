package app.ageguessr.features.game.ReportRound;

import app.ageguessr.features.hints.HintCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param reason one of PhotoReportReason or HintReportReason, by name, depending on the
 *     target: checked by the handler, the two lists being different
 * @param hintCategory which of the hints shown, for a HINT report. Optional when the round
 *     showed a single one (rounds played before several hints, a page loaded before the
 *     release); ignored for a PHOTO report
 */
public record ReportRoundCommand(
        @NotNull ReportTarget target, @NotBlank @Size(max = 24) String reason, HintCategory hintCategory) {}
