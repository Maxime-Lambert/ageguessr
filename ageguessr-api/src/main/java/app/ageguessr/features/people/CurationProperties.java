package app.ageguessr.features.people;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param allowLivingApproval false until image rights for living people are settled
 *     (docs/decisions/architecture.md): photos of a living person can then be reviewed
 *     and rejected, never approved.
 */
@ConfigurationProperties("app.curation")
public record CurationProperties(boolean allowLivingApproval) {

    /** Same rule for the queue (to grey out "Approve") and for the review itself. */
    public boolean canApprove(Person person) {
        return allowLivingApproval || person.getDeathDate() != null;
    }
}
