package app.ageguessr.features.people.ReviewPhoto;

import app.ageguessr.features.people.ReviewStatus;
import jakarta.validation.constraints.NotNull;

/** PENDING undoes a previous decision. */
public record ReviewPhotoCommand(@NotNull ReviewStatus decision) {}
