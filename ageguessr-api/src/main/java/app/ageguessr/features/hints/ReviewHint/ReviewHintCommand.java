package app.ageguessr.features.hints.ReviewHint;

import app.ageguessr.features.hints.HintReviewStatus;
import app.ageguessr.features.hints.HintText;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PENDING undoes a previous decision. A null text leaves that text as it is. */
public record ReviewHintCommand(
        @NotNull HintReviewStatus decision,
        @Size(max = HintText.MAX_LENGTH) String textEn,
        @Size(max = HintText.MAX_LENGTH) String textFr) {}
