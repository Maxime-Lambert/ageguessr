package app.ageguessr.features.people.IngestPeople;

/**
 * Outcome of --fix-small-images: of the {@code found} photos under the minimum height,
 * how many got a taller copy of the same file and how many were rejected ({@code
 * approvedRejected} of them had been approved).
 */
record SmallImagesReport(int found, int enlarged, int rejected, int approvedRejected) {

    @Override
    public String toString() {
        return "small images found=" + found + ", enlarged=" + enlarged + ", rejected=" + rejected
                + " (of which approved=" + approvedRejected + ")";
    }
}
