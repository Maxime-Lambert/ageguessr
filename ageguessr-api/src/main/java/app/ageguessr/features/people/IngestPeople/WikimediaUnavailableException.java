package app.ageguessr.features.people.IngestPeople;

/** A Wikimedia service kept failing after every retry. Ends the ingestion of the current person or page. */
class WikimediaUnavailableException extends RuntimeException {

    WikimediaUnavailableException(String message) {
        super(message);
    }

    WikimediaUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
