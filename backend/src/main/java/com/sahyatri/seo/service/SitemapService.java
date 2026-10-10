package com.sahyatri.seo.service;

import com.sahyatri.blog.service.BlogPublicService;
import com.sahyatri.catalog.dto.CatalogTrek;
import com.sahyatri.catalog.service.CatalogService;
import com.sahyatri.common.config.AppProperties;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The sitemap Google reads (docs/TRD.md §7.16): the site's static public pages, every trek in the public catalog,
 * the guides leading its upcoming departures and every published blog post (§7.19). Departures themselves are left
 * out because they expire.
 */
@Service
public class SitemapService {

    /** Mirrors the public routes in frontend/src/router.tsx and SITE_LINKS. */
    static final List<String> STATIC_PATHS =
            List.of("/", "/treks", "/guides", "/blog", "/vision", "/faqs", "/cancellations", "/contact", "/terms", "/privacy", "/cookies", "/credits");

    private final CatalogService catalog;
    private final BlogPublicService blog;
    private final String siteOrigin;

    public SitemapService(CatalogService catalog, BlogPublicService blog, AppProperties app) {
        this.catalog = catalog;
        this.blog = blog;
        this.siteOrigin = app.frontendBaseUrl().replaceAll("/+$", "");
    }

    public String sitemap() {
        Set<String> paths = new LinkedHashSet<>(STATIC_PATHS);
        List<CatalogTrek> treks = catalog.catalog();
        treks.forEach(t -> paths.add("/treks/" + t.slug()));
        treks.forEach(t -> t.departures().forEach(d -> paths.add("/guides/" + d.guide().id())));
        blog.publishedSlugs().forEach(slug -> paths.add("/blog/" + slug));

        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String path : paths) {
            xml.append("  <url><loc>").append(escape(siteOrigin + path)).append("</loc></url>\n");
        }
        return xml.append("</urlset>\n").toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
