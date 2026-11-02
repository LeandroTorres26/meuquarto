package com.meuquarto.profile.api;

import com.meuquarto.profile.application.Avatars;
import com.meuquarto.profile.domain.AvatarKey;
import com.meuquarto.shared.storage.ObjectStorage;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves avatars to anyone (they are on public profiles). The bucket itself stays private: the browser
 * only ever asks this endpoint, through the Next.js proxy, so the CSP needs no new host. The name in the
 * path must be exactly one we generated; anything else is a 404 and storage is not touched. A name never
 * changes content (a new upload gets a new name), so the response may be cached for a year.
 */
@RestController
@RequestMapping("/api/public/avatars")
public class PublicAvatarController {

    private final Avatars avatars;

    public PublicAvatarController(Avatars avatars) {
        this.avatars = avatars;
    }

    @GetMapping("/{name}")
    public ResponseEntity<byte[]> avatar(@PathVariable String name) {
        ObjectStorage.StoredObject stored = avatars.serve(name);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(AvatarKey.CONTENT_TYPE))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .body(stored.content());
    }
}
