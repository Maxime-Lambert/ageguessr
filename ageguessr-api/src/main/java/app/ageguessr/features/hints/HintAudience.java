package app.ageguessr.features.hints;

/**
 * Who can read a hint. ALL carries both texts, FR and EN only their own: a French chart
 * means little to an English-speaking player. The minimum of hints per year counts ALL
 * hints plus those of the audience.
 */
public enum HintAudience {
    ALL,
    FR,
    EN
}
