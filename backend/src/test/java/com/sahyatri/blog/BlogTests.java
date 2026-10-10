package com.sahyatri.blog;

import com.jayway.jsonpath.JsonPath;
import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin-written blog with categories one level deep, drafts, publishing and photos (docs/TRD.md §7.19). */
class BlogTests extends AuthTestSupport {

    private static final String ADMIN = "/api/admin/blog";
    private static final String PUBLIC = "/api/public/blog";

    @Test
    void categoriesAreOneLevelDeepAndOnlyEmptyOnesGo() throws Exception {
        String admin = adminToken();
        String top = category(admin, "Kedarkantha", null);
        String sub = category(admin, "Winter", top);

        // A sub-category can't have its own, and a category with sub-categories can't become one.
        createCategory(admin, "Deeper", sub).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.parent_id").exists());
        String other = category(admin, "Snow", null);
        authed(put(ADMIN + "/categories/" + top), admin, categoryJson("Kedarkantha", slug(), other))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.parent_id").exists());

        // Slugs are unique.
        String taken = categorySlug(admin, top);
        createCategory(admin, "Copy", null, taken).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLUG_TAKEN"));

        // In use: sub-categories, then posts.
        authed(delete(ADMIN + "/categories/" + top), admin, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
        String post = newPost(admin, sub, slug());
        authed(delete(ADMIN + "/categories/" + sub), admin, null).andExpect(status().isConflict());
        authed(delete(ADMIN + "/posts/" + post), admin, null).andExpect(status().isNoContent());
        authed(delete(ADMIN + "/categories/" + sub), admin, null).andExpect(status().isNoContent());
        authed(delete(ADMIN + "/categories/" + top), admin, null).andExpect(status().isNoContent());

        // Admin only.
        String trekker = bookingTrekker();
        authed(post(ADMIN + "/categories"), trekker, categoryJson("X", slug(), null)).andExpect(status().isForbidden());
        mockMvc.perform(post(ADMIN + "/categories")).andExpect(status().isUnauthorized());
    }

    @Test
    void draftsAreHiddenUntilPublished() throws Exception {
        String admin = adminToken();
        String top = category(admin, "Treks", null);
        String sub = category(admin, "Kedarkantha", top);
        String topSlug = categorySlug(admin, top);
        String postSlug = slug();
        String post = newPost(admin, sub, postSlug);

        mockMvc.perform(get(PUBLIC + "/posts/" + postSlug)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        mockMvc.perform(get(PUBLIC + "/posts").param("category", topSlug)).andExpect(jsonPath("$.items", empty()));

        authed(put(ADMIN + "/posts/" + post + "/published"), admin, "{\"published\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published_at").exists());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action = 'BLOG_POST_PUBLISHED' AND entity_id = ?",
                Integer.class, UUID.fromString(post))).isEqualTo(1);

        // Filed under the sub-category, so the parent's filter finds it too.
        mockMvc.perform(get(PUBLIC + "/posts").param("category", topSlug))
                .andExpect(jsonPath("$.items[*].slug", contains(postSlug)))
                .andExpect(jsonPath("$.items[0].category.name").value("Kedarkantha"))
                .andExpect(jsonPath("$.items[0].category.parent.slug").value(topSlug))
                .andExpect(jsonPath("$.items[0].body").doesNotExist());
        mockMvc.perform(get(PUBLIC + "/posts/" + postSlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Winter on Kedarkantha"))
                .andExpect(jsonPath("$.body").value("First paragraph.\n\n## Day one\nSnow."))
                .andExpect(jsonPath("$.author_name").exists());
        mockMvc.perform(get(PUBLIC + "/categories"))
                .andExpect(jsonPath("$.items[?(@.id == '" + sub + "')].published_posts", contains(1)));
        mockMvc.perform(get(PUBLIC + "/posts").param("category", "no-such-category")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/sitemap.xml"))
                .andExpect(content().string(containsString("/blog/" + postSlug + "</loc>")));

        authed(put(ADMIN + "/posts/" + post + "/published"), admin, "{\"published\":false}").andExpect(status().isOk());
        mockMvc.perform(get(PUBLIC + "/posts/" + postSlug)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/sitemap.xml"))
                .andExpect(content().string(not(containsString("/blog/" + postSlug + "</loc>"))));
    }

    @Test
    void postsAreValidated() throws Exception {
        String admin = adminToken();
        String cat = category(admin, "Snow", null);
        String taken = slug();
        String post = newPost(admin, cat, taken);

        createPost(admin, cat, taken).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLUG_TAKEN"));
        createPost(admin, UUID.randomUUID().toString(), slug()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.category_id").exists());
        createPost(admin, cat, "Not A Slug").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.slug").exists());

        // Editing keeps its own slug.
        authed(put(ADMIN + "/posts/" + post), admin, blogPostJson(cat, taken).replace("Winter on Kedarkantha", "Edited"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Edited"))
                .andExpect(jsonPath("$.published_at").value(nullValue()));
        authed(get(ADMIN + "/posts"), admin, null).andExpect(jsonPath("$.items[?(@.id == '" + post + "')].title",
                contains("Edited")));
    }

    @Test
    void photosBelongToTheirPostAndOneIsTheCover() throws Exception {
        String admin = adminToken();
        String cat = category(admin, "Photos", null);
        String postSlug = slug();
        String post = newPost(admin, cat, postSlug);
        String other = newPost(admin, cat, slug());

        String first = photoId(authed(photoRequest(post, image(3000, 2000)).param("caption", " Summit "), admin, null));
        String second = photoId(authed(photoRequest(post, image(100, 100)), admin, null));
        mockMvc.perform(get("/api/public/files/blog-photos/" + first + ".jpg")).andExpect(status().isOk());

        authed(put(ADMIN + "/posts/" + post + "/cover"), admin, "{\"photo_id\":\"" + first + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cover_url").value(containsString(first)))
                .andExpect(jsonPath("$.photos[*].id", contains(first, second)))
                .andExpect(jsonPath("$.photos[0].caption").value("Summit"));
        // Another post's photo can't be the cover.
        authed(put(ADMIN + "/posts/" + other + "/cover"), admin, "{\"photo_id\":\"" + first + "\"}")
                .andExpect(status().isBadRequest());

        // Deleting the cover photo clears the cover and the file.
        authed(delete(ADMIN + "/posts/" + post + "/photos/" + first), admin, null).andExpect(status().isNoContent());
        authed(get(ADMIN + "/posts/" + post), admin, null)
                .andExpect(jsonPath("$.cover_url").value(nullValue()))
                .andExpect(jsonPath("$.photos", hasSize(1)));
        mockMvc.perform(get("/api/public/files/blog-photos/" + first + ".jpg")).andExpect(status().isNotFound());

        // Deleting the post takes its photos with it.
        authed(delete(ADMIN + "/posts/" + post), admin, null).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/public/files/blog-photos/" + second + ".jpg")).andExpect(status().isNotFound());
        assertThat(UPLOAD_DIR.resolve("blog-photos").resolve(second + ".jpg")).doesNotExist();
        authed(get(ADMIN + "/posts"), admin, null)
                .andExpect(jsonPath("$.items[*].id", not(containsInAnyOrder(post))));
    }

    private String category(String token, String name, String parentId) throws Exception {
        return id(createCategory(token, name, parentId).andExpect(status().isCreated()));
    }

    private ResultActions createCategory(String token, String name, String parentId) throws Exception {
        return createCategory(token, name, parentId, slug());
    }

    private ResultActions createCategory(String token, String name, String parentId, String slug) throws Exception {
        return authed(post(ADMIN + "/categories"), token, categoryJson(name, slug, parentId));
    }

    private String categorySlug(String token, String id) throws Exception {
        String body = authed(get(ADMIN + "/categories"), token, null).andReturn().getResponse().getContentAsString();
        return JsonPath.<java.util.List<String>>read(body, "$.items[?(@.id == '" + id + "')].slug").getFirst();
    }

    private static String categoryJson(String name, String slug, String parentId) {
        return """
                {"name":"%s","slug":"%s","parent_id":%s}""".formatted(name, slug, parentId == null ? "null" : "\"" + parentId + "\"");
    }

    private String newPost(String token, String categoryId, String slug) throws Exception {
        return id(createPost(token, categoryId, slug).andExpect(status().isCreated()));
    }

    private ResultActions createPost(String token, String categoryId, String slug) throws Exception {
        return authed(post(ADMIN + "/posts"), token, blogPostJson(categoryId, slug));
    }

    private static String blogPostJson(String categoryId, String slug) {
        return """
                {"title":"Winter on Kedarkantha","slug":"%s","category_id":"%s","excerpt":" ",
                 "body":"  First paragraph.\\n\\n## Day one\\nSnow.  "}""".formatted(slug, categoryId);
    }

    private static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder photoRequest(
            String post, byte[] bytes) {
        return multipart(ADMIN + "/posts/" + post + "/photos")
                .file(new MockMultipartFile("file", "photo", "application/octet-stream", bytes));
    }

    private static String photoId(ResultActions result) throws Exception {
        return id(result.andExpect(status().isCreated()));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static String slug() {
        return "blog-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static byte[] image(int width, int height) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out)).isTrue();
        return out.toByteArray();
    }
}
