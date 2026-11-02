package com.meuquarto.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Keys are random and ours; names read from a URL are accepted only in exactly that shape. */
class AvatarKeyTest {

    @Test
    void generatedKeysAreRandomUuidsUnderTheAvatarsPrefix() {
        AvatarKey first = AvatarKey.generate();
        AvatarKey second = AvatarKey.generate();
        assertThat(first.value()).matches("avatars/[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.jpg");
        assertThat(first).isNotEqualTo(second);
        assertThat(first.publicName()).isEqualTo(first.value().substring("avatars/".length()));
    }

    @Test
    void aPublicNameWeGeneratedComesBackAsTheSameKey() {
        AvatarKey key = AvatarKey.generate();
        assertThat(AvatarKey.fromPublicName(key.publicName())).contains(key);
    }

    @Test
    void anythingElseIsNotAKey() {
        List<String> bad = List.of("", "x", "avatar.jpg", "../secret.jpg", "..%2Fsecret.jpg",
                "avatars/11111111-1111-1111-1111-111111111111.jpg",
                "11111111-1111-1111-1111-111111111111.png",
                "11111111-1111-1111-1111-111111111111.jpg/",
                "11111111-1111-1111-1111-111111111111.jpg\n",
                "11111111-1111-1111-1111-111111111111.jpg.exe",
                "AAAAAAAA-1111-1111-1111-111111111111.jpg",
                "1111111111111111111111111111111111111.jpg");
        for (String name : bad) {
            assertThat(AvatarKey.fromPublicName(name)).as(name).isEmpty();
        }
        assertThat(AvatarKey.fromPublicName(null)).isEmpty();
        assertThatThrownBy(() -> new AvatarKey("avatars/../x.jpg")).isInstanceOf(IllegalArgumentException.class);
    }
}
