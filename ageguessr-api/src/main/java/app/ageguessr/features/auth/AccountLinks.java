package app.ageguessr.features.auth;

import app.ageguessr.shared.i18n.Language;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Links of the account emails, to the site in the account's language: /fr/… or /en/…
 * (the site has one URL prefix per language). An account that never chose a language gets
 * the English site; Caddy would redirect a link without a prefix anyway.
 */
@Component
public class AccountLinks {

    private final String frontendUrl;

    public AccountLinks(@Value("${app.frontend-url}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    public String to(User user, String path, String rawToken) {
        return frontendUrl + "/" + Language.orDefault(user.getLanguage()).code() + path + "?token=" + rawToken;
    }
}
