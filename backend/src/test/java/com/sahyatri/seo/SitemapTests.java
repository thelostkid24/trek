package com.sahyatri.seo;

import com.sahyatri.auth.AuthTestSupport;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** docs/TRD.md §7.16. */
class SitemapTests extends AuthTestSupport {

    private static final String SITE = "http://localhost:5173";

    private String slugOf(UUID trackId) {
        return jdbc.queryForObject("SELECT slug FROM tracks WHERE id = ?", String.class, trackId);
    }

    @Test
    void listsStaticPagesPublicTreksAndTheirGuidesButNotDepartures() throws Exception {
        String admin = adminToken();
        UUID guide = guideUser();
        UUID published = createTrack(admin, 1);
        UUID departure = createDraft(admin, published, guide, today().plusDays(20), 199_900, 8);
        authed(post("/api/admin/departures/" + departure + "/publish"), admin, null).andExpect(status().isOk());
        UUID hidden = createTrack(admin, 1);

        mockMvc.perform(get("/api/public/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(header().string("Cache-Control", containsString("max-age=3600")))
                .andExpect(content().string(containsString("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">")))
                .andExpect(content().string(containsString("<loc>" + SITE + "/</loc>")))
                .andExpect(content().string(containsString("<loc>" + SITE + "/faqs</loc>")))
                .andExpect(content().string(containsString("<loc>" + SITE + "/treks/" + slugOf(published) + "</loc>")))
                .andExpect(content().string(containsString("<loc>" + SITE + "/guides/" + guide + "</loc>")))
                .andExpect(content().string(not(containsString("/treks/" + slugOf(hidden) + "<"))))
                .andExpect(content().string(not(containsString("/departures/"))));
    }

    @Test
    void apiRobotsAllowsOnlyTheSitemapAndPublicFiles() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string(containsString("Allow: /api/public/sitemap.xml")))
                .andExpect(content().string(containsString("Allow: /api/public/files/")))
                .andExpect(content().string(containsString("Disallow: /\n")));
    }
}
