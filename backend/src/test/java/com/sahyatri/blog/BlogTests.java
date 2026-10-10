package com.sahyatri.blog;

import com.jayway.jsonpath.JsonPath;
import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The blog (docs/TRD.md §7.19): ten seeded categories, posts under exactly one sub-category, publishing rules, the
 * category thresholds, one slug namespace with redirects, and photos. Tests share a database, so each works in a
 * sub-category of its own.
 */
class BlogTests extends AuthTestSupport {

    private static final String ADMIN = "/api/admin/blog";
    private static final String PUBLIC = "/api/public/blog";

    @Test
    void theTenCategoriesAreSeededAndPostsGoUnderASubCategory() throws Exception {
        mockMvc.perform(get(PUBLIC + "/categories"))
                .andExpect(jsonPath("$.items", hasSize(10)))
                .andExpect(jsonPath("$.items[0].slug").value("himalayan-treks"))
                .andExpect(jsonPath("$.items[0].subcategories[0].slug").value("trek-guides"))
                .andExpect(jsonPath("$.items[9].slug").value("trek-experiences"));

        String admin = adminToken();
        String top = categoryId(admin, "plan-your-trek");
        createPost(admin, top, slug(), "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.category_id").exists());

        // Sub-categories go under one of the ten, never under a sub-category or at the top.
        authed(post(ADMIN + "/categories"), admin, categoryJson("Loose", slug(), null)).andExpect(status().isBadRequest());
        String sub = subCategory(admin, top);
        authed(post(ADMIN + "/categories"), admin, categoryJson("Deeper", slug(), sub)).andExpect(status().isBadRequest());
        authed(post(ADMIN + "/categories"), admin, categoryJson("Copy", "trek-guides", top))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLUG_TAKEN"));

        // The ten stay; a sub-category goes once it's empty.
        authed(delete(ADMIN + "/categories/" + top), admin, null).andExpect(status().isConflict());
        String post = newPost(admin, sub, slug());
        authed(delete(ADMIN + "/categories/" + sub), admin, null).andExpect(status().isConflict());
        authed(delete(ADMIN + "/posts/" + post), admin, null).andExpect(status().isNoContent());
        authed(delete(ADMIN + "/categories/" + sub), admin, null).andExpect(status().isNoContent());

        String trekker = bookingTrekker();
        authed(get(ADMIN + "/posts"), trekker, null).andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN + "/posts")).andExpect(status().isUnauthorized());
    }

    @Test
    void publishingNeedsTheHeroImageExcerptAndTheMoneyRule() throws Exception {
        String admin = adminToken();
        String sub = subCategory(admin, categoryId(admin, "gear-fitness-packing"));
        String postSlug = slug();
        String post = id(authed(post(ADMIN + "/posts"), admin, """
                {"title":"Packing for Kedarkantha","slug":"%s","category_id":"%s","body":"Layers."}""".formatted(postSlug, sub))
                .andExpect(status().isCreated()));

        publish(admin, post, true).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.cover_photo_id").exists())
                .andExpect(jsonPath("$.details.fields.cover_caption").exists())
                .andExpect(jsonPath("$.details.fields.cover_taken_on").exists())
                .andExpect(jsonPath("$.details.fields.excerpt").exists())
                .andExpect(jsonPath("$.details.fields.money_rule_confirmed").doesNotExist());

        makeReady(admin, post, sub, postSlug);
        publish(admin, post, false).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.money_rule_confirmed").exists());
        publish(admin, post, true).andExpect(status().isOk()).andExpect(jsonPath("$.published_at").exists());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action = 'BLOG_POST_PUBLISHED' AND entity_id = ?",
                Integer.class, UUID.fromString(post))).isEqualTo(1);

        // Public: everything but the research notes.
        mockMvc.perform(get(PUBLIC + "/posts/" + postSlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cover_caption").value("Juda ka Talab, frozen"))
                .andExpect(jsonPath("$.cover_taken_on").value("2026-01-12"))
                .andExpect(jsonPath("$.faqs[0].question").value("Is it cold?"))
                .andExpect(jsonPath("$.schema_type").value("FAQ_PAGE"))
                .andExpect(jsonPath("$.author_name").value("Shray Rai"))
                .andExpect(jsonPath("$.research_notes").value(nullValue()));
        authed(get(ADMIN + "/posts/" + post), admin, null).andExpect(jsonPath("$.research_notes").value("Asked Shray."));

        authed(put(ADMIN + "/posts/" + post + "/published"), admin, "{\"published\":false}").andExpect(status().isOk());
        mockMvc.perform(get(PUBLIC + "/posts/" + postSlug)).andExpect(status().isNotFound());
    }

    @Test
    void aCategoryPageOpensAtOnePostAndJoinsTheMenuAtThree() throws Exception {
        String admin = adminToken();
        String sub = subCategory(admin, categoryId(admin, "responsible-trekking"));
        String subSlug = slugOf(admin, sub);

        mockMvc.perform(get(PUBLIC + "/categories/" + subSlug)).andExpect(status().isNotFound());
        String first = publishedPost(admin, sub);
        mockMvc.perform(get(PUBLIC + "/categories/" + subSlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexable").value(false))
                .andExpect(jsonPath("$.parent.slug").value("responsible-trekking"))
                .andExpect(jsonPath("$.posts[*].slug", contains(first)));
        mockMvc.perform(get("/api/public/sitemap.xml"))
                .andExpect(content().string(containsString("/blog/" + first + "</loc>")))
                .andExpect(content().string(not(containsString("/" + subSlug + "</loc>"))));

        publishedPost(admin, sub);
        String third = publishedPost(admin, sub);
        mockMvc.perform(get(PUBLIC + "/categories/" + subSlug))
                .andExpect(jsonPath("$.indexable").value(true))
                .andExpect(jsonPath("$.posts", hasSize(3)))
                .andExpect(jsonPath("$.posts[0].slug").value(third));
        mockMvc.perform(get(PUBLIC + "/categories"))
                .andExpect(jsonPath("$.items[?(@.slug == 'responsible-trekking')].in_menu", contains(true)))
                .andExpect(jsonPath("$.items[?(@.slug == 'responsible-trekking')].subcategories[?(@.slug == '" + subSlug + "')].published_posts",
                        contains(3)));
        mockMvc.perform(get(PUBLIC + "/categories/responsible-trekking")).andExpect(jsonPath("$.indexable").value(true));
        mockMvc.perform(get("/api/public/sitemap.xml"))
                .andExpect(content().string(containsString("/blog/responsible-trekking/" + subSlug + "</loc>")));
    }

    @Test
    void postsAndCategoriesShareOneSlugSpaceAndOldUrlsRedirect() throws Exception {
        String admin = adminToken();
        String sub = subCategory(admin, categoryId(admin, "trek-experiences"));

        createPost(admin, sub, "trek-experiences", "").andExpect(status().isConflict());
        String postSlug = slug();
        String post = newPost(admin, sub, postSlug);
        authed(post(ADMIN + "/categories"), admin, categoryJson("Clash", postSlug, categoryId(admin, "trek-experiences")))
                .andExpect(status().isConflict());

        // A draft's slug change leaves nothing behind; a published post's old slug redirects.
        String draftSlug = slug();
        updatePost(admin, post, sub, draftSlug).andExpect(status().isOk());
        mockMvc.perform(get(PUBLIC + "/posts/" + postSlug)).andExpect(status().isNotFound());
        makeReady(admin, post, sub, draftSlug);
        publish(admin, post, true).andExpect(status().isOk());
        String newSlug = slug();
        updatePost(admin, post, sub, newSlug).andExpect(status().isOk());
        mockMvc.perform(get(PUBLIC + "/posts/" + draftSlug))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/api/public/blog/posts/" + newSlug));
        createPost(admin, sub, draftSlug, "").andExpect(status().isConflict());

        // Taking the old slug back removes its redirect.
        updatePost(admin, post, sub, draftSlug).andExpect(status().isOk());
        mockMvc.perform(get(PUBLIC + "/posts/" + draftSlug)).andExpect(status().isOk());
        mockMvc.perform(get(PUBLIC + "/posts/" + newSlug)).andExpect(status().isMovedPermanently());
    }

    @Test
    void postsLinkToTheirTreks() throws Exception {
        String admin = adminToken();
        UUID track = createTrack(admin, 2);
        String sub = subCategory(admin, categoryId(admin, "himalayan-treks"));
        String postSlug = slug();
        String post = newPost(admin, sub, postSlug);
        authed(put(ADMIN + "/posts/" + post), admin, postJson(sub, postSlug, "\"track_ids\":[\"" + track + "\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.related_treks[0].id").value(track.toString()));
        authed(put(ADMIN + "/posts/" + post), admin, postJson(sub, postSlug, "\"track_ids\":[\"" + UUID.randomUUID() + "\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.track_ids").exists());
    }

    @Test
    void photosBelongToTheirPostAndOneIsTheHero() throws Exception {
        String admin = adminToken();
        String sub = subCategory(admin, categoryId(admin, "villages-and-culture"));
        String post = newPost(admin, sub, slug());
        String other = newPost(admin, sub, slug());

        String first = id(authed(photoRequest(post, image(3000, 2000)).param("caption", " Summit "), admin, null)
                .andExpect(status().isCreated()));
        String second = id(authed(photoRequest(post, image(100, 100)), admin, null).andExpect(status().isCreated()));
        mockMvc.perform(get("/api/public/files/blog-photos/" + first + ".jpg")).andExpect(status().isOk());

        authed(put(ADMIN + "/posts/" + post + "/cover"), admin, "{\"photo_id\":\"" + first + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cover_url").value(containsString(first)))
                .andExpect(jsonPath("$.photos[*].id", contains(first, second)))
                .andExpect(jsonPath("$.photos[0].caption").value("Summit"));
        authed(put(ADMIN + "/posts/" + other + "/cover"), admin, "{\"photo_id\":\"" + first + "\"}")
                .andExpect(status().isBadRequest());

        authed(delete(ADMIN + "/posts/" + post + "/photos/" + first), admin, null).andExpect(status().isNoContent());
        authed(get(ADMIN + "/posts/" + post), admin, null)
                .andExpect(jsonPath("$.cover_url").value(nullValue()))
                .andExpect(jsonPath("$.photos", hasSize(1)));
        mockMvc.perform(get("/api/public/files/blog-photos/" + first + ".jpg")).andExpect(status().isNotFound());

        authed(delete(ADMIN + "/posts/" + post), admin, null).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/public/files/blog-photos/" + second + ".jpg")).andExpect(status().isNotFound());
        assertThat(UPLOAD_DIR.resolve("blog-photos").resolve(second + ".jpg")).doesNotExist();
    }

    // --- Helpers

    private String categoryId(String token, String slug) throws Exception {
        String body = authed(get(ADMIN + "/categories"), token, null).andReturn().getResponse().getContentAsString();
        return JsonPath.<List<String>>read(body, "$.items[?(@.slug == '" + slug + "')].id").getFirst();
    }

    private String slugOf(String token, String id) throws Exception {
        String body = authed(get(ADMIN + "/categories"), token, null).andReturn().getResponse().getContentAsString();
        return JsonPath.<List<String>>read(body, "$.items[?(@.id == '" + id + "')].slug").getFirst();
    }

    private String subCategory(String token, String parentId) throws Exception {
        return id(authed(post(ADMIN + "/categories"), token, categoryJson("Test " + slug(), slug(), parentId))
                .andExpect(status().isCreated()));
    }

    private static String categoryJson(String name, String slug, String parentId) {
        return """
                {"name":"%s","slug":"%s","parent_id":%s}""".formatted(name, slug, parentId == null ? "null" : "\"" + parentId + "\"");
    }

    private String newPost(String token, String categoryId, String slug) throws Exception {
        return id(createPost(token, categoryId, slug, "").andExpect(status().isCreated()));
    }

    private ResultActions createPost(String token, String categoryId, String slug, String extra) throws Exception {
        return authed(post(ADMIN + "/posts"), token, postJson(categoryId, slug, extra));
    }

    private ResultActions updatePost(String token, String id, String categoryId, String slug) throws Exception {
        return authed(put(ADMIN + "/posts/" + id), token, postJson(categoryId, slug, READY));
    }

    private static final String READY = """
            "excerpt":"Two nights at Juda ka Talab.","cover_caption":"Juda ka Talab, frozen","cover_taken_on":"2026-01-12",
            "author_name":"Shray Rai","schema_type":"FAQ_PAGE","research_notes":"Asked Shray.",
            "faqs":[{"question":"Is it cold?","answer":"Minus ten at night."}]""";

    private static String postJson(String categoryId, String slug, String extra) {
        return """
                {"title":"Winter on Kedarkantha","slug":"%s","category_id":"%s","body":"First paragraph."%s}"""
                .formatted(slug, categoryId, extra.isEmpty() ? "" : "," + extra);
    }

    /** Fills in what publishing needs: the hero image with caption and date, and the excerpt. */
    private void makeReady(String token, String post, String categoryId, String slug) throws Exception {
        authed(put(ADMIN + "/posts/" + post), token, postJson(categoryId, slug, READY)).andExpect(status().isOk());
        String photo = id(authed(photoRequest(post, image(40, 30)), token, null).andExpect(status().isCreated()));
        authed(put(ADMIN + "/posts/" + post + "/cover"), token, "{\"photo_id\":\"" + photo + "\"}").andExpect(status().isOk());
    }

    private String publishedPost(String token, String categoryId) throws Exception {
        String slug = slug();
        String post = newPost(token, categoryId, slug);
        makeReady(token, post, categoryId, slug);
        publish(token, post, true).andExpect(status().isOk());
        return slug;
    }

    private ResultActions publish(String token, String post, boolean confirmed) throws Exception {
        return authed(put(ADMIN + "/posts/" + post + "/published"), token,
                "{\"published\":true,\"money_rule_confirmed\":" + confirmed + "}");
    }

    private static MockMultipartHttpServletRequestBuilder photoRequest(String post, byte[] bytes) {
        return multipart(ADMIN + "/posts/" + post + "/photos")
                .file(new MockMultipartFile("file", "photo", "application/octet-stream", bytes));
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
