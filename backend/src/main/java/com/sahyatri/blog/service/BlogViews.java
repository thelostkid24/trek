package com.sahyatri.blog.service;

import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.blog.dto.BlogCategoryRef;
import com.sahyatri.blog.dto.BlogFaq;
import com.sahyatri.blog.dto.BlogPhotoResponse;
import com.sahyatri.blog.dto.BlogPostResponse;
import com.sahyatri.blog.dto.BlogPostSummary;
import com.sahyatri.blog.dto.BlogTrekRef;
import com.sahyatri.blog.entity.BlogCategory;
import com.sahyatri.blog.entity.BlogPhoto;
import com.sahyatri.blog.entity.BlogPost;
import com.sahyatri.blog.repository.BlogCategoryRepository;
import com.sahyatri.blog.repository.BlogPhotoRepository;
import com.sahyatri.common.storage.BlogPhotoFiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Turns posts into what the admin and public endpoints return. Callers hold the transaction. */
@Component
class BlogViews {

    private final BlogCategoryRepository categories;
    private final BlogPhotoRepository photos;
    private final UserRepository users;
    private final BlogPhotoFiles files;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    BlogViews(BlogCategoryRepository categories, BlogPhotoRepository photos, UserRepository users, BlogPhotoFiles files,
              JdbcTemplate jdbc, ObjectMapper json) {
        this.categories = categories;
        this.photos = photos;
        this.users = users;
        this.files = files;
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Every category by id; there are few, so lists resolve theirs from one read. */
    Map<UUID, BlogCategory> categoriesById() {
        return categories.findAll().stream().collect(Collectors.toMap(BlogCategory::getId, Function.identity()));
    }

    BlogCategoryRef ref(UUID categoryId, Map<UUID, BlogCategory> all) {
        BlogCategory c = all.get(categoryId);
        if (c == null) {
            return null;
        }
        return new BlogCategoryRef(c.getId(), c.getName(), c.getSlug(),
                c.getParentId() == null ? null : ref(c.getParentId(), all));
    }

    List<BlogPostSummary> summaries(List<BlogPost> posts) {
        Map<UUID, BlogCategory> all = categoriesById();
        return posts.stream().map(p -> new BlogPostSummary(p.getId(), p.getSlug(), p.getTitle(), p.getExcerpt(),
                ref(p.getCategoryId(), all), coverUrl(p), p.getPublishedAt(), p.getUpdatedAt())).toList();
    }

    /** The whole post; research notes only for the admin. */
    BlogPostResponse full(BlogPost p, boolean admin) {
        List<BlogPhotoResponse> photoList = photos.findByPostIdOrderByCreatedAtAscIdAsc(p.getId()).stream()
                .map(this::photo)
                .toList();
        String author = p.getAuthorName() != null ? p.getAuthorName()
                : users.findById(p.getAuthorId()).map(User::getFullName).orElse(null);
        return new BlogPostResponse(p.getId(), p.getSlug(), p.getTitle(), p.getExcerpt(), p.getBody(),
                ref(p.getCategoryId(), categoriesById()), p.getCoverPhotoId(), coverUrl(p), p.getCoverCaption(),
                p.getCoverTakenOn(), photoList, author, faqs(p.getFaqs()), p.getSchemaType(), relatedTreks(p.getId()),
                admin ? p.getResearchNotes() : null, p.getPublishedAt(), p.getUpdatedAt());
    }

    BlogPhotoResponse photo(BlogPhoto photo) {
        return new BlogPhotoResponse(photo.getId(), files.url(photo.getId()), photo.getCaption());
    }

    List<BlogTrekRef> relatedTreks(UUID postId) {
        return jdbc.query("""
                        SELECT t.id, t.slug, t.name FROM blog_post_treks b JOIN tracks t ON t.id = b.track_id
                        WHERE b.post_id = ? ORDER BY t.name""",
                (rs, i) -> new BlogTrekRef(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name")),
                postId);
    }

    String faqsJson(List<BlogFaq> faqs) {
        return json.writeValueAsString(faqs == null ? List.of() : faqs.stream()
                .map(f -> new BlogFaq(f.question().trim(), f.answer().trim()))
                .toList());
    }

    private List<BlogFaq> faqs(String stored) {
        return json.readValue(stored, new TypeReference<List<BlogFaq>>() {
        });
    }

    private String coverUrl(BlogPost p) {
        return p.getCoverPhotoId() == null ? null : files.url(p.getCoverPhotoId());
    }
}
