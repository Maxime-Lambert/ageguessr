package app.ageguessr.features.people;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Re-encoded bytes of a {@link PersonPhoto}, stripped of every EXIF/XMP/IPTC field. Kept
 * in its own table because Hibernate cannot lazy-load a bytea column without bytecode
 * enhancement.
 */
@Entity
@Table(name = "person_photo_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhotoImage {

    /** Same value as the owning PersonPhoto's id. */
    @Id
    private UUID photoId;

    private byte[] content;

    private String contentType;

    private int width;

    private int height;
}
