package app.ageguessr.features.auth.ChangeLanguage;

import app.ageguessr.shared.i18n.Language;
import jakarta.validation.constraints.NotNull;

public record ChangeLanguageCommand(@NotNull Language language) {}
