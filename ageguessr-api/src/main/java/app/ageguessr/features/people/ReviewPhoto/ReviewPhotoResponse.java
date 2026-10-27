package app.ageguessr.features.people.ReviewPhoto;

import app.ageguessr.features.people.ReviewStatus;
import java.util.UUID;

public record ReviewPhotoResponse(UUID photoId, ReviewStatus reviewStatus) {}
