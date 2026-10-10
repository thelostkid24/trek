package com.sahyatri.blog.controller;

import com.sahyatri.blog.dto.BlogCategoryResponse;
import com.sahyatri.blog.dto.BlogPostResponse;
import com.sahyatri.blog.dto.BlogPostSummary;
import com.sahyatri.blog.service.BlogPublicService;
import com.sahyatri.common.web.ItemsResponse;
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
    public ItemsResponse<BlogCategoryResponse> categories() {
        return new ItemsResponse<>(blog.categories());
    }

    @GetMapping("/posts")
    public ItemsResponse<BlogPostSummary> posts(@RequestParam(required = false) String category) {
        return new ItemsResponse<>(blog.posts(category));
    }

    @GetMapping("/posts/{slug}")
    public BlogPostResponse post(@PathVariable String slug) {
        return blog.post(slug);
    }
}
