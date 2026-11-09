package app.ageguessr.features.auth;

import app.ageguessr.shared.i18n.Language;

/**
 * Every word of the account emails, in both languages, kept apart from the sending so
 * that a third language means changing this file only. French uses "vous", like the site.
 * Each text takes the link already built; durations match AccountEmails' token lifetimes.
 */
final class AccountEmailTexts {

    record Text(String subject, String html) {}

    private AccountEmailTexts() {}

    static Text emailVerification(Language language, String link) {
        return language == Language.FR
                ? new Text(
                        "Confirmez votre compte AgeGuessr",
                        "<p>Cliquez <a href=\"%s\">ici</a> pour confirmer votre adresse email.</p>".formatted(link))
                : new Text(
                        "Confirm your AgeGuessr account",
                        "<p>Click <a href=\"%s\">here</a> to confirm your email address.</p>".formatted(link));
    }

    static Text passwordReset(Language language, String link) {
        return language == Language.FR
                ? new Text(
                        "Réinitialisez votre mot de passe AgeGuessr",
                        ("<p>Quelqu'un a demandé à réinitialiser le mot de passe de votre compte AgeGuessr.</p>"
                                        + "<p><a href=\"%s\">Choisir un nouveau mot de passe</a> (ce lien expire dans"
                                        + " 1 heure).</p>"
                                        + "<p>Si ce n'était pas vous, ignorez cet email : votre mot de passe ne change"
                                        + " pas.</p>")
                                .formatted(link))
                : new Text(
                        "Reset your AgeGuessr password",
                        ("<p>Someone asked to reset the password of your AgeGuessr account.</p>"
                                        + "<p><a href=\"%s\">Choose a new password</a> (this link expires in 1 hour).</p>"
                                        + "<p>If it wasn't you, ignore this email: your password stays the same.</p>")
                                .formatted(link));
    }

    static Text passwordChanged(Language language, String link) {
        return language == Language.FR
                ? new Text(
                        "Le mot de passe de votre compte AgeGuessr a été changé",
                        ("<p>Le mot de passe de votre compte AgeGuessr vient d'être changé.</p>"
                                        + "<p>Si c'était vous, il n'y a rien à faire.</p>"
                                        + "<p><strong>Ce n'était pas vous ?</strong> <a href=\"%s\">Sécurisez votre"
                                        + " compte</a> : choisissez un nouveau mot de passe, et tous les appareils"
                                        + " seront déconnectés (ce lien expire dans 24 heures).</p>")
                                .formatted(link))
                : new Text(
                        "Your AgeGuessr password was changed",
                        ("<p>The password of your AgeGuessr account was just changed.</p>"
                                        + "<p>If it was you, there is nothing to do.</p>"
                                        + "<p><strong>Wasn't you?</strong> <a href=\"%s\">Secure your account</a>: choose a"
                                        + " new password and every device will be logged out (this link expires in"
                                        + " 24 hours).</p>")
                                .formatted(link));
    }

    static Text deletionConfirmation(Language language, String link) {
        return language == Language.FR
                ? new Text(
                        "Confirmez la suppression de votre compte AgeGuessr",
                        ("<p>Vous avez demandé la suppression de votre compte AgeGuessr. C'est définitif.</p>"
                                        + "<p><a href=\"%s\">Confirmer la suppression</a> (ce lien expire dans 1"
                                        + " heure).</p>"
                                        + "<p>Si ce n'était pas vous, ignorez cet email et changez votre mot de passe :"
                                        + " quelqu'un le connaît.</p>")
                                .formatted(link))
                : new Text(
                        "Confirm the deletion of your AgeGuessr account",
                        ("<p>You asked to delete your AgeGuessr account. This cannot be undone.</p>"
                                        + "<p><a href=\"%s\">Confirm the deletion</a> (this link expires in 1 hour).</p>"
                                        + "<p>If it wasn't you, ignore this email and change your password: someone knows"
                                        + " it.</p>")
                                .formatted(link));
    }
}
