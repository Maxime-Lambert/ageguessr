package app.ageguessr.features.people;

import app.ageguessr.shared.reports.ReportStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A player's report of a photo (V16). Anonymous on purpose: neither the player nor their
 * IP, only what is reported and why. One report per played round is enforced on the
 * round (RoundProgress), not here.
 */
@Entity
@Table(name = "photo_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class PhotoReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID photoId;

    @Enumerated(EnumType.STRING)
    private PhotoReportReason reason;

    @Enumerated(EnumType.STRING)
    private ReportStatus status;

    private Instant createdAt;

    private Instant closedAt;

    public static PhotoReport open(UUID photoId, PhotoReportReason reason, Instant now) {
        return PhotoReport.builder()
                .photoId(photoId)
                .reason(reason)
                .status(ReportStatus.OPEN)
                .createdAt(now)
                .build();
    }
}
