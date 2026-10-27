package app.ageguessr.features.people;

/** A photo waiting for curation, with its person, as read by the curation queue. */
public record PendingPhoto(PersonPhoto photo, Person person) {}
