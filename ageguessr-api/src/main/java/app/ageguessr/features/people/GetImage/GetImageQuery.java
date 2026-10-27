package app.ageguessr.features.people.GetImage;

/** {@code publicId} as received: parsed by the handler so a malformed id looks like an unknown one. */
public record GetImageQuery(String publicId) {}
