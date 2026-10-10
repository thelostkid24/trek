package com.sahyatri.blog.repository;

import com.sahyatri.blog.entity.BlogSlugRedirect;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogSlugRedirectRepository extends JpaRepository<BlogSlugRedirect, String> {
}
