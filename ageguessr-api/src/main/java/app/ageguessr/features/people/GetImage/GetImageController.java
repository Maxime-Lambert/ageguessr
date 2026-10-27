package app.ageguessr.features.people.GetImage;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anti-cheat proxy: the game never links to Commons, whose file names usually contain
 * the person's name. Only the bytes go out - no file name (no Content-Disposition), no
 * Commons hash, no source URL. Public: the game is playable without an account.
 */
@RestController
@RequiredArgsConstructor
public class GetImageController {

    static final Duration CACHE_DURATION = Duration.ofDays(1);

    private final GetImageHandler handler;

    @GetMapping("/api/images/{publicId}")
    ResponseEntity<byte[]> image(@PathVariable String publicId) {
        ImageResponse image = handler.handle(new GetImageQuery(publicId));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(CACHE_DURATION).cachePublic())
                .body(image.content());
    }
}
