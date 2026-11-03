package app.ageguessr.features.hints;

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
 * A player's report of a hint (V16). Anonymous on purpose: neither the player nor their
 * IP, only what is reported and why. One report per played round is enforced on the
 * round (RoundProgress), not here.
 */
@Entity
@Table(name = "hint_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class HintReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID hintId;

    @Enumerated(EnumType.STRING)
    private HintReportReason reason;

    @Enumerated(EnumType.STRING)
    private ReportStatus status;

    private Instant createdAt;

    private Instant closedAt;

    public static HintReport open(UUID hintId, HintReportReason reason, Instant now) {
        return HintReport.builder()
                .hintId(hintId)
                .reason(reason)
                .status(ReportStatus.OPEN)
                .createdAt(now)
                .build();
    }
}
