package com.meuquarto.profile.api;

import com.meuquarto.profile.application.Avatars;
import com.meuquarto.profile.domain.AvatarException.Problem;
import com.meuquarto.shared.security.CurrentUserId;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * The signed-in owner's avatar (S6, FR-05): upload or replace, and remove. The owner is the session user.
 * The file name and content type the client sends are ignored; the use case decides from the bytes.
 */
@RestController
@RequestMapping("/api/profile/avatar")
public class AvatarController {

    private final Avatars avatars;

    public AvatarController(Avatars avatars) {
        this.avatars = avatars;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AvatarResponse upload(@CurrentUserId long userId, @RequestParam("file") MultipartFile file)
            throws IOException {
        return new AvatarResponse(avatars.upload(userId, file.getBytes()));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@CurrentUserId long userId) {
        avatars.remove(userId);
    }

    public record AvatarResponse(String avatarUrl) {}

    /** Tomcat refused the upload for its size before our code saw it. Same code as the use case's own check. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorBody> tooLarge() {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(new ErrorBody(Problem.AVATAR_TOO_LARGE.name()));
    }

    /** The request had no {@code file} part. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ErrorBody> missingFile() {
        return ResponseEntity.badRequest().body(new ErrorBody(Problem.AVATAR_EMPTY.name()));
    }

    record ErrorBody(String error) {}
}
