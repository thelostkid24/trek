package com.sahyatri.blog.repository;

import com.sahyatri.blog.entity.BlogPost;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlogPostRepository extends JpaRepository<BlogPost, UUID> {

    List<BlogPost> findAllByOrderByUpdatedAtDesc();

    List<BlogPost> findByPublishedAtNotNullOrderByPublishedAtDesc(Limit limit);

    List<BlogPost> findByPublishedAtNotNullAndCategoryIdInOrderByPublishedAtDesc(Collection<UUID> categoryIds);

    Optional<BlogPost> findBySlugAndPublishedAtNotNull(String slug);

    boolean existsBySlug(String slug);

    boolean existsByCategoryId(UUID categoryId);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    /** Published posts per (sub-)category. */
    @Query("select p.categoryId as categoryId, count(p) as posts from BlogPost p where p.publishedAt is not null group by p.categoryId")
    List<CategoryCount> countPublishedByCategory();

    interface CategoryCount {
        UUID getCategoryId();

        long getPosts();
    }
}
