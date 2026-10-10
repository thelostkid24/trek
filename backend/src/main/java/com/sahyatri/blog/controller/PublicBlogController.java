package com.sahyatri.blog.controller;

import com.sahyatri.blog.dto.BlogCategoryNode;
import com.sahyatri.blog.dto.BlogCategoryPage;
import com.sahyatri.blog.dto.BlogPostResponse;
import com.sahyatri.blog.dto.BlogPostSummary;
import com.sahyatri.blog.service.BlogPublicService;
import com.sahyatri.common.web.ItemsResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Contract: docs/TRD.md §7.19. Published posts only. */
@RestController
@RequestMapping("/api/public/blog")
public class PublicBlogController {

    private final BlogPublicService blog;

    public PublicBlogController(BlogPublicService blog) {
        this.blog = blog;
    }

    @GetMapping("/categories")
    public ItemsResponse<BlogCategoryNode> categories() {
        return new ItemsResponse<>(blog.tree());
    }

    @GetMapping("/categories/{slug}")
    public BlogCategoryPage category(@PathVariable String slug) {
        return blog.category(slug);
    }

    @GetMapping("/posts")
    public ItemsResponse<BlogPostSummary> posts(@RequestParam(required = false) Integer limit) {
        return new ItemsResponse<>(blog.latest(limit));
    }

    /** A slug the post gave up answers 301 to its current one, so old links (and the client) land on it. */
    @GetMapping("/posts/{slug}")
    public ResponseEntity<BlogPostResponse> post(@PathVariable String slug) {
        BlogPublicService.Resolved found = blog.post(slug);
        if (found.movedTo() != null) {
            return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                    .header(HttpHeaders.LOCATION, "/api/public/blog/posts/" + found.movedTo())
                    .build();
        }
        return ResponseEntity.ok(found.post());
    }
}
