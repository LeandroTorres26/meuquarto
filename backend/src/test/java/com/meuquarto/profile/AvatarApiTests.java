package com.meuquarto.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.meuquarto.shared.storage.ObjectStorage;
import com.meuquarto.support.RealCsrf;
import com.meuquarto.support.StorageTestcontainersConfiguration;
import com.meuquarto.support.TestImages;
import com.meuquarto.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The avatar (S6, FR-05) against a real PostgreSQL and a real S3-compatible server: upload, replace, remove,
 * serve, account deletion, and what is refused.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, StorageTestcontainersConfiguration.class})
class AvatarApiTests {

    private static final String PASSWORD = "cavalo bateria grampo";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectStorage storage;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from users");
        jdbc.update("delete from spring_session");
    }

    private Cookie signUp(String email, String slug) throws Exception {
        return mvc.perform(post("/api/auth/signup").with(RealCsrf.token(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"slug\":\"%s\"}".formatted(email, PASSWORD, slug)))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");
    }

    private ResultActions upload(Cookie session, byte[] content, String fileName, String contentType) throws Exception {
        var request = multipart("/api/profile/avatar").file(new MockMultipartFile("file", fileName, contentType, content))
                .with(RealCsrf.token(mvc));
        if (session != null) {
            request.cookie(session);
        }
        return mvc.perform(request);
    }

    private ResultActions uploadPhoto(Cookie session) throws Exception {
        return upload(session, TestImages.jpeg(TestImages.halves(900, 600, Color.RED, Color.BLUE)), "foto.jpg", "image/jpeg");
    }

    private String storedKey(String slug) {
        return jdbc.queryForObject("select avatar_key from profiles where slug = ?", String.class, slug);
    }

    private static String urlOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.avatarUrl");
    }

    // --- upload ---

    @Test
    void anUploadIsStoredPrivatelyAndTheDatabaseKeepsOnlyTheKey() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");

        ResultActions response = uploadPhoto(session).andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(org.hamcrest.Matchers.startsWith("/api/public/avatars/")));
        String url = urlOf(response);

        String key = storedKey("leandro");
        assertThat(key).matches("avatars/[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.jpg");
        assertThat(url).isEqualTo("/api/public/avatars/" + key.substring("avatars/".length()));
        ObjectStorage.StoredObject stored = storage.get(key).orElseThrow();
        assertThat(stored.contentType()).isEqualTo("image/jpeg");
        BufferedImage image = TestImages.decode(stored.content());
        assertThat(image.getWidth()).isEqualTo(512);
        assertThat(image.getHeight()).isEqualTo(512);
    }

    @Test
    void theAvatarIsServedToAnyoneWithSafeHeaders() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        String url = urlOf(uploadPhoto(session));

        byte[] body = mvc.perform(get(url)) // no session, no CSRF: anonymous
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=31536000")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("public")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(TestImages.decode(body).getWidth()).isEqualTo(512);
    }

    @Test
    void thePublicProfileCarriesTheAvatarUrlAndFollowsChanges() throws Exception {
        Cookie session = signUp("a@example.com", "avatar-one");
        mvc.perform(get("/api/public/profiles/avatar-one")).andExpect(jsonPath("$.avatarUrl").doesNotExist()); // primes the cache

        String url = urlOf(uploadPhoto(session));

        mvc.perform(get("/api/public/profiles/avatar-one")).andExpect(jsonPath("$.avatarUrl").value(url));
        mvc.perform(get("/api/profile").cookie(session)).andExpect(jsonPath("$.avatarUrl").value(url));
    }

    @Test
    void replacingTheAvatarGivesANewKeyAndDeletesTheOldObject() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        uploadPhoto(session).andExpect(status().isOk());
        String first = storedKey("leandro");

        uploadPhoto(session).andExpect(status().isOk());
        String second = storedKey("leandro");

        assertThat(second).isNotEqualTo(first);
        assertThat(storage.get(first)).isEmpty();
        assertThat(storage.get(second)).isPresent();
        mvc.perform(get("/api/public/avatars/" + first.substring("avatars/".length()))).andExpect(status().isNotFound());
    }

    @Test
    void removingTheAvatarClearsTheKeyAndDeletesTheObject() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        uploadPhoto(session).andExpect(status().isOk());
        String key = storedKey("leandro");

        mvc.perform(delete("/api/profile/avatar").cookie(session).with(RealCsrf.token(mvc)))
                .andExpect(status().isNoContent());

        assertThat(storedKey("leandro")).isNull();
        assertThat(storage.get(key)).isEmpty();
        mvc.perform(get("/api/public/profiles/leandro")).andExpect(jsonPath("$.avatarUrl").doesNotExist());
        // Removing again is not an error.
        mvc.perform(delete("/api/profile/avatar").cookie(session).with(RealCsrf.token(mvc)))
                .andExpect(status().isNoContent());
    }

    @Test
    void metadataOfTheOriginalNeverReachesStorage() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        byte[] jpeg = TestImages.jpeg(TestImages.halves(300, 300, Color.RED, Color.BLUE));
        jpeg = TestImages.withJpegSegment(jpeg, 0xE1, TestImages.exifPayload(1, "HOME-ADDRESS-SECRET"));

        upload(session, jpeg, "foto.jpg", "image/jpeg").andExpect(status().isOk());

        byte[] stored = storage.get(storedKey("leandro")).orElseThrow().content();
        assertThat(TestImages.contains(stored, "HOME-ADDRESS-SECRET")).isFalse();
        assertThat(TestImages.contains(stored, "Exif")).isFalse();
    }

    @Test
    void thePngIsReEncodedAndTheFileNameAndDeclaredTypeAreIgnored() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        byte[] png = TestImages.png(TestImages.halves(300, 200, Color.RED, Color.BLUE));

        upload(session, png, "../../etc/passwd.html", "text/html").andExpect(status().isOk());

        String key = storedKey("leandro");
        assertThat(key).doesNotContain("passwd").doesNotContain("html");
        assertThat(storage.get(key).orElseThrow().contentType()).isEqualTo("image/jpeg");
    }

    // --- refusals ---

    @Test
    void invalidFilesAreRefusedWithAClearCodeAndNothingIsStored() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");

        upload(session, "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8), "avatar.jpg", "image/jpeg")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("AVATAR_UNSUPPORTED_TYPE"));
        upload(session, new byte[0], "avatar.png", "image/png")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("AVATAR_EMPTY"));
        upload(session, "RIFF$\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1), "a.webp", "image/webp")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("AVATAR_UNSUPPORTED_TYPE"));
        upload(session, TestImages.pngHeaderOnly(30_000, 30_000), "bomb.png", "image/png")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("AVATAR_DIMENSIONS_TOO_LARGE"));
        byte[] png = TestImages.png(TestImages.halves(300, 300, Color.RED, Color.BLUE));
        upload(session, java.util.Arrays.copyOf(png, png.length / 2), "cut.png", "image/png")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("AVATAR_UNREADABLE"));
        upload(session, new byte[3 * 1024 * 1024], "huge.jpg", "image/jpeg")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("AVATAR_TOO_LARGE"));

        assertThat(storedKey("leandro")).isNull();
    }

    @Test
    void aRefusedUploadKeepsTheCurrentAvatar() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        uploadPhoto(session).andExpect(status().isOk());
        String key = storedKey("leandro");

        upload(session, "nope".getBytes(StandardCharsets.UTF_8), "x.jpg", "image/jpeg").andExpect(status().isBadRequest());

        assertThat(storedKey("leandro")).isEqualTo(key);
        assertThat(storage.get(key)).isPresent();
    }

    @Test
    void aRequestWithoutTheFilePartIsRefused() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        mvc.perform(multipart("/api/profile/avatar").cookie(session).with(RealCsrf.token(mvc)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("AVATAR_EMPTY"));
    }

    // --- serving: keys ---

    @Test
    void namesThatAreNotOurKeysAreNotFound() throws Exception {
        for (String name : List.of("foo.jpg", "x", "11111111-1111-1111-1111-111111111111.png",
                "AAAAAAAA-1111-1111-1111-111111111111.jpg", "11111111-1111-1111-1111-111111111111.jpg.exe")) {
            mvc.perform(get("/api/public/avatars/" + name)).andExpect(status().isNotFound());
        }
        // Encoded slashes never reach the controller: the firewall refuses them (a 4xx either way, never a file).
        for (String name : List.of("..", "..%2F..%2Fetc%2Fpasswd", "%2e%2e%2f%2e%2e%2fsecret.jpg")) {
            mvc.perform(get("/api/public/avatars/" + name)).andExpect(status().is4xxClientError());
        }
        mvc.perform(get("/api/public/avatars/11111111-1111-1111-1111-111111111111.jpg")).andExpect(status().isNotFound());
    }

    // --- authorization ---

    @Test
    void anonymousCannotUploadOrRemove() throws Exception {
        upload(null, TestImages.jpeg(TestImages.halves(100, 100, Color.RED, Color.BLUE)), "a.jpg", "image/jpeg")
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/profile/avatar").with(RealCsrf.token(mvc))).andExpect(status().isUnauthorized());
    }

    @Test
    void uploadAndRemoveNeedTheCsrfToken() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        byte[] jpeg = TestImages.jpeg(TestImages.halves(100, 100, Color.RED, Color.BLUE));
        mvc.perform(multipart("/api/profile/avatar").file(new MockMultipartFile("file", "a.jpg", "image/jpeg", jpeg))
                .cookie(session)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/profile/avatar").cookie(session)).andExpect(status().isForbidden());
        assertThat(storedKey("leandro")).isNull();
    }

    @Test
    void oneUserNeverTouchesAnotherUsersAvatar() throws Exception {
        Cookie ana = signUp("a@example.com", "ana");
        Cookie bia = signUp("b@example.com", "bia");
        uploadPhoto(ana).andExpect(status().isOk());
        String anasKey = storedKey("ana");

        // Bia's upload and removal only ever act on Bia's profile.
        uploadPhoto(bia).andExpect(status().isOk());
        mvc.perform(delete("/api/profile/avatar").cookie(bia).with(RealCsrf.token(mvc))).andExpect(status().isNoContent());

        assertThat(storedKey("ana")).isEqualTo(anasKey);
        assertThat(storage.get(anasKey)).isPresent();
        assertThat(storedKey("bia")).isNull();
    }

    // --- account deletion ---

    @Test
    void deletingTheAccountDeletesTheAvatarObject() throws Exception {
        Cookie session = signUp("a@example.com", "leandro");
        uploadPhoto(session).andExpect(status().isOk());
        String key = storedKey("leandro");
        assertThat(storage.get(key)).isPresent();

        mvc.perform(post("/api/account/delete").cookie(session).with(RealCsrf.token(mvc))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isNoContent());

        assertThat(storage.get(key)).isEmpty();
        mvc.perform(get("/api/public/profiles/leandro")).andExpect(status().isNotFound());
    }
}
