package com.meuquarto.profile.api;

import com.meuquarto.profile.application.ProfileEditor;
import com.meuquarto.shared.security.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in owner's profile editor (S6, FR-05). Every route needs a session; the profile is always the
 * session user's, there is no id in the path or the body. The Bean Validation limits only bound the input;
 * the exact rules (lengths, slug, theme) live in the domain.
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileEditor editor;

    public ProfileController(ProfileEditor editor) {
        this.editor = editor;
    }

    @GetMapping
    public ProfileResponse view(@CurrentUserId long userId) {
        return ProfileResponse.of(editor.view(userId));
    }

    /** Replaces name, bio, slug and theme; the avatar has its own routes. */
    @PutMapping
    public ProfileResponse update(@CurrentUserId long userId, @Valid @RequestBody UpdateRequest body) {
        return ProfileResponse.of(editor.update(userId, body.displayName(), body.bio(), body.slug(), body.theme()));
    }

    public record UpdateRequest(
            @NotNull @Size(max = 200) String displayName,
            @Size(max = 2000) String bio,
            @NotNull @Size(max = 64) String slug,
            @NotNull @Size(max = 30) String theme) {}

    /** {@code avatarUrl} is null without an avatar; {@code availableThemes} are the ids the user may apply. */
    public record ProfileResponse(String slug, String displayName, String bio, String avatarUrl, String theme,
            List<String> availableThemes) {

        static ProfileResponse of(ProfileEditor.Editable editable) {
            return new ProfileResponse(editable.slug(), editable.displayName(), editable.bio(), editable.avatarUrl(),
                    editable.theme(), editable.availableThemes());
        }
    }
}
