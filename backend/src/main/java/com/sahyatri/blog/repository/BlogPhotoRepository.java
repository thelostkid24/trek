package com.sahyatri.blog.repository;

import com.sahyatri.blog.entity.BlogPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlogPhotoRepository extends JpaRepository<BlogPhoto, UUID> {

    List<BlogPhoto> findByPostIdOrderByCreatedAtAscIdAsc(UUID postId);

    long countByPostId(UUID postId);

    Optional<BlogPhoto> findByIdAndPostId(UUID id, UUID postId);
}
