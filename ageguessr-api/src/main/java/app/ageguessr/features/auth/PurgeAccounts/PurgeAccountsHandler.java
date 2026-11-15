package app.ageguessr.features.auth.PurgeAccounts;

import app.ageguessr.features.auth.AccountEmails;
import app.ageguessr.features.auth.AccountRetentionProperties;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Deletes the accounts kept for nothing (GDPR storage limitation): never verified after
 * 7 days, inactive for 3 years once warned 30 days before (AccountRetentionProperties).
 * A deletion is the DELETE of the users row, whose cascades remove the rest, as when the
 * player deletes the account. Not one transaction: each email and each step stands alone,
 * so that one failure leaves the rest done.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurgeAccountsHandler {

    private static final Duration HALF_A_DAY = Duration.ofHours(12);

    private final UserRepository users;
    private final AccountEmails emails;
    private final AccountRetentionProperties retention;
    private final Clock clock;

    public record Result(int unverifiedDeleted, int warned, int inactiveDeleted) {}

    public Result purge() {
        Instant now = clock.instant();
        Duration inactivity = Duration.ofDays(retention.inactiveDays());
        Duration notice = Duration.ofDays(retention.warningDays());

        int unverified = users.deleteUnverifiedCreatedBefore(now.minus(Duration.ofDays(retention.unverifiedDays())));
        // Deleted before warning: an account warned today is never deleted today. Half a
        // day of margin on the notice: a run starts a few milliseconds earlier or later from
        // one day to the next, and the deletion must fall on the announced day, not before.
        int inactive = users.deleteInactive(now.minus(inactivity), now.minus(notice).minus(HALF_A_DAY));
        int warned = 0;
        // The run 30 days after the warning finds it a few hours short: the one after deletes.
        Instant deletionDate = now.plus(notice).plus(Duration.ofDays(1));
        for (User user : users.findInactiveToWarn(now.minus(inactivity).plus(notice))) {
            try {
                emails.sendInactivityWarning(user, deletionDate);
                users.markWarned(user.getId(), now);
                warned++;
            } catch (RuntimeException e) {
                // Not marked: warned on the next run, and never deleted before 30 days after.
                log.error("Could not warn an inactive account ({})", user.getId(), e);
            }
        }
        return new Result(unverified, warned, inactive);
    }
}
