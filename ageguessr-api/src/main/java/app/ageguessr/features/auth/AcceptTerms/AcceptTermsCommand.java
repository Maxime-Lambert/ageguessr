package app.ageguessr.features.auth.AcceptTerms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param version the version the player was shown: the "Last updated" date of /terms */
public record AcceptTermsCommand(@NotBlank @Size(max = 20) String version) {}
