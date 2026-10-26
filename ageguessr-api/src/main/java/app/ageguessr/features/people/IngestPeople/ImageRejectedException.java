package app.ageguessr.features.people.IngestPeople;

/** A selected photo whose image could not be fetched or safely re-encoded. Only that photo is skipped. */
class ImageRejectedException extends RuntimeException {

    enum Reason {
        HOST_NOT_ALLOWED,
        DOWNLOAD_FAILED,
        TOO_LARGE,
        UNREADABLE
    }

    private final Reason reason;

    ImageRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    Reason reason() {
        return reason;
    }
}
