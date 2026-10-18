-- Blog post SEO fields from the Kedarkantha article pack (Notion "18 oct changes", docs/TRD.md §6.16, §7.19):
-- a title for the browser tab and Google when the post title is too long, alt text for the hero image, and 3 to 6
-- tags (shown on the post; tag pages come later).
ALTER TABLE blog_posts
    ADD COLUMN seo_title TEXT CHECK (char_length(seo_title) <= 70),
    ADD COLUMN cover_alt TEXT CHECK (char_length(cover_alt) <= 200),
    ADD COLUMN tags      JSONB NOT NULL DEFAULT '[]';
