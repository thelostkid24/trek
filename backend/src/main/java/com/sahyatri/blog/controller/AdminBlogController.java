package com.sahyatri.blog.controller;

import com.sahyatri.blog.dto.BlogCategoryRequest;
import com.sahyatri.blog.dto.BlogCategoryResponse;
import com.sahyatri.blog.dto.BlogCoverRequest;
import com.sahyatri.blog.dto.BlogPhotoRequest;
import com.sahyatri.blog.dto.BlogPhotoResponse;
import com.sahyatri.blog.dto.BlogPostRequest;
import com.sahyatri.blog.dto.BlogPostResponse;
import com.sahyatri.blog.dto.BlogPostSummary;
import com.sahyatri.blog.dto.BlogPublishRequest;
import com.sahyatri.blog.service.BlogAdminService;
import com.sahyatri.blog.service.BlogPublicService;
import com.sahyatri.common.web.ItemsResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** Contract: docs/TRD.md §7.19. */
@RestController
@RequestMapping("/api/admin/blog")
public class AdminBlogController {

    private final BlogAdminService blog;
    private final BlogPublicService reads;

    public AdminBlogController(BlogAdminService blog, BlogPublicService reads) {
        this.blog = blog;
        this.reads = reads;
    }

    @GetMapping("/categories")
    public ItemsResponse<BlogCategoryResponse> categories() {
        return new ItemsResponse<>(reads.categories());
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public BlogCategoryResponse createCategory(@Valid @RequestBody BlogCategoryRequest req) {
        return blog.createCategory(req);
    }

    @PutMapping("/categories/{id}")
    public BlogCategoryResponse updateCategory(@PathVariable UUID id, @Valid @RequestBody BlogCategoryRequest req) {
        return blog.updateCategory(id, req);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable UUID id) {
        blog.deleteCategory(id);
    }

    @GetMapping("/posts")
    public ItemsResponse<BlogPostSummary> posts() {
        return new ItemsResponse<>(blog.posts());
    }

    @GetMapping("/posts/{id}")
    public BlogPostResponse post(@PathVariable UUID id) {
        return blog.post(id);
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public BlogPostResponse createPost(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BlogPostRequest req) {
        return blog.createPost(userId(jwt), req);
    }

    @PutMapping("/posts/{id}")
    public BlogPostResponse updatePost(@PathVariable UUID id, @Valid @RequestBody BlogPostRequest req) {
        return blog.updatePost(id, req);
    }

    @PutMapping("/posts/{id}/published")
    public BlogPostResponse setPublished(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                         @Valid @RequestBody BlogPublishRequest req) {
        return blog.setPublished(userId(jwt), id, req.published());
    }

    @PutMapping("/posts/{id}/cover")
    public BlogPostResponse setCover(@PathVariable UUID id, @RequestBody BlogCoverRequest req) {
        return blog.setCover(id, req.photoId());
    }

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        blog.deletePost(userId(jwt), id);
    }

    @PostMapping(path = "/posts/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BlogPhotoResponse uploadPhoto(@PathVariable UUID id, @RequestPart("file") MultipartFile file,
                                         @RequestParam(required = false) String caption) {
        return blog.uploadPhoto(id, file, caption);
    }

    @PutMapping("/posts/{id}/photos/{photoId}")
    public BlogPhotoResponse describePhoto(@PathVariable UUID id, @PathVariable UUID photoId,
                                           @Valid @RequestBody BlogPhotoRequest req) {
        return blog.describePhoto(id, photoId, req.caption());
    }

    @DeleteMapping("/posts/{id}/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
        blog.deletePhoto(id, photoId);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
