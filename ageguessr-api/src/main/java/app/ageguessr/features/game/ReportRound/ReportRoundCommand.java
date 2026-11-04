package app.ageguessr.features.game.ReportRound;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param reason one of PhotoReportReason or HintReportReason, by name, depending on the
 *     target: checked by the handler, the two lists being different
 */
public record ReportRoundCommand(@NotNull ReportTarget target, @NotBlank @Size(max = 24) String reason) {}
