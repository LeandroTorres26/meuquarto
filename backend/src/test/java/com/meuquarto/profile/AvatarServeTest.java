package com.meuquarto.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meuquarto.profile.application.Avatars;
import com.meuquarto.profile.domain.AvatarException;
import com.meuquarto.profile.domain.AvatarException.Problem;
import com.meuquarto.profile.domain.AvatarKey;
import com.meuquarto.support.InMemoryObjectStorage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Serving an avatar by the name in the URL: only names we generate reach storage. No Spring, no database. */
class AvatarServeTest {

    private final InMemoryObjectStorage storage = new InMemoryObjectStorage();
    // serve() only uses storage; the other collaborators belong to the write paths.
    private final Avatars avatars = new Avatars(null, storage, null, null);

    @Test
    void namesThatAreNotOurKeysAreNotFoundAndStorageIsNeverAsked() {
        for (String name : List.of("..%2F..%2Fetc%2Fpasswd", "../x.jpg", "x.jpg", "avatar", "", " ",
                "11111111-1111-1111-1111-111111111111.png", "11111111-1111-1111-1111-111111111111.jpg/../a",
                "AAAAAAAA-1111-1111-1111-111111111111.jpg", "avatars/11111111-1111-1111-1111-111111111111.jpg")) {
            assertThatThrownBy(() -> avatars.serve(name)).as(name).isInstanceOfSatisfying(AvatarException.class,
                    e -> assertThat(e.problem()).isEqualTo(Problem.AVATAR_NOT_FOUND));
        }
        assertThatThrownBy(() -> avatars.serve(null)).isInstanceOf(AvatarException.class);
        assertThat(storage.calls()).isEmpty();
    }

    @Test
    void aWellFormedNameWithNoObjectIsNotFoundAfterOneRead() {
        AvatarKey key = AvatarKey.generate();
        assertThatThrownBy(() -> avatars.serve(key.publicName())).isInstanceOfSatisfying(AvatarException.class,
                e -> assertThat(e.problem()).isEqualTo(Problem.AVATAR_NOT_FOUND));
        assertThat(storage.calls()).containsExactly("get " + key.value());
    }

    @Test
    void anExistingAvatarIsReturned() {
        AvatarKey key = AvatarKey.generate();
        storage.put(key.value(), new byte[] {1, 2, 3}, "image/jpeg");
        assertThat(avatars.serve(key.publicName()).content()).containsExactly(1, 2, 3);
    }
}
