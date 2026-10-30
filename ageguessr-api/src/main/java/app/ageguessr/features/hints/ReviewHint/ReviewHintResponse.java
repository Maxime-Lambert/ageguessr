package app.ageguessr.features.hints.ReviewHint;

import app.ageguessr.features.hints.HintReviewStatus;
import java.util.UUID;

public record ReviewHintResponse(UUID hintId, HintReviewStatus reviewStatus, String textEn, String textFr) {}
