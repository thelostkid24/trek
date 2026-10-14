-- Blog: the ten categories and 41 sub-categories from "The Empty Valley — Blog categories" (Notion "14 oct changes"),
-- replacing V20's seed (docs/TRD.md §6.16, §7.19). Posts move to the closest new sub-category; a sub-category an
-- admin added moves under the closest new category; then the V20 seed goes.

CREATE TEMPORARY TABLE seed (pos INT, slug TEXT, name TEXT, subs TEXT[][]);
INSERT INTO seed VALUES
 (1, 'your-treks', 'Your Treks', ARRAY[
   ['uttarakhand-treks', 'Uttarakhand Treks'], ['himachal-treks', 'Himachal Treks'], ['more-states', 'More states']]),
 (2, 'best-treks', 'Best Treks', ARRAY[
   ['by-season', 'By Season'], ['by-difficulty', 'By Difficulty Level'], ['by-type', 'By Type'],
   ['by-place', 'By Place'], ['comparisons', 'See the Comparisons']]),
 (3, 'plan-your-trek', 'Plan Your Trek', ARRAY[
   ['weather-and-snow', 'Weather & Snow'], ['getting-there', 'Getting There'], ['permits-and-rules', 'Permits & Rules'],
   ['costs', 'Costs'], ['booking', 'Booking']]),
 (4, 'gear-and-packing', 'Gear & Packing', ARRAY[
   ['packing-list', 'Packing List'], ['clothing', 'Clothing'], ['shoes', 'Shoes'],
   ['equipment-and-rentals', 'Equipment & Rentals']]),
 (5, 'fitness', 'Fitness', ARRAY[
   ['training', 'Training'], ['am-i-fit-enough', 'Am I Fit Enough?'], ['getting-ready', 'Getting Ready']]),
 (6, 'health-and-safety', 'Health & Safety', ARRAY[
   ['altitude-sickness', 'Altitude Sickness'], ['first-aid', 'First Aid'], ['medical-conditions', 'Medical Conditions'],
   ['dangers-and-rescue', 'Dangers & Rescue']]),
 (7, 'on-the-trail', 'On the Trail', ARRAY[
   ['food-and-water', 'Food & Water'], ['toilets-and-hygiene', 'Toilets & Hygiene'], ['camping', 'Camping'],
   ['phone-and-photos', 'Phone & Photos'], ['trail-skills', 'Trail Skills']]),
 (8, 'who-can-trek', 'Who Can Trek?', ARRAY[
   ['first-timers', 'First-Timers'], ['solo-and-women', 'Solo & Women'], ['families-and-kids', 'Families & Kids'],
   ['seniors-and-groups', 'Seniors & Groups']]),
 (9, 'mountain-culture', 'Mountain Culture', ARRAY[
   ['villages-and-people', 'Villages & People'], ['myths-and-temples', 'Myths & Temples'],
   ['wildlife-and-nature', 'Wildlife & Nature'], ['local-village-stories', 'Local Village Stories'],
   ['responsible-trekking', 'Responsible Trekking']]),
 (10, 'updates', 'Updates', ARRAY[
   ['trip-reports', 'Trip Reports'], ['trail-conditions', 'Trail Conditions'], ['news', 'News']]);

-- Old slug → new slug, for every V20 category (top-level ones map to a top-level one).
CREATE TEMPORARY TABLE moves (old_slug TEXT PRIMARY KEY, new_slug TEXT NOT NULL);
INSERT INTO moves VALUES
 ('himalayan-treks', 'your-treks'), ('plan-your-trek', 'plan-your-trek'), ('gear-fitness-packing', 'gear-and-packing'),
 ('altitude-and-safety', 'health-and-safety'), ('life-on-the-trail', 'on-the-trail'),
 ('trekking-for-beginners', 'who-can-trek'), ('snow-and-trail-updates', 'updates'),
 ('villages-and-culture', 'mountain-culture'), ('responsible-trekking', 'mountain-culture'),
 ('trek-experiences', 'updates'),
 ('trek-guides', 'uttarakhand-treks'), ('routes-and-itineraries', 'uttarakhand-treks'),
 ('difficulty-and-grading', 'by-difficulty'), ('trek-comparisons', 'comparisons'), ('which-trek', 'by-type'),
 ('best-time-and-season', 'by-season'), ('permits-and-documents', 'permits-and-rules'),
 ('where-to-stay', 'getting-there'), ('trekker-budget', 'costs'), ('choosing-an-operator', 'booking'),
 ('trip-planning-basics', 'booking'),
 ('packing-lists', 'packing-list'), ('gear-and-equipment', 'equipment-and-rentals'),
 ('renting-vs-buying', 'equipment-and-rentals'), ('fitness-and-training', 'training'),
 ('altitude-sickness', 'altitude-sickness'), ('first-aid-and-emergencies', 'first-aid'),
 ('wildlife-safety', 'dangers-and-rescue'), ('insurance-and-evacuation', 'dangers-and-rescue'),
 ('womens-health', 'medical-conditions'), ('medical-conditions', 'medical-conditions'),
 ('food-and-water', 'food-and-water'), ('hygiene', 'toilets-and-hygiene'), ('camping-and-sleeping', 'camping'),
 ('network-and-electricity', 'phone-and-photos'), ('weather-and-cold', 'weather-and-snow'),
 ('trail-etiquette', 'trail-skills'),
 ('first-time-trekkers', 'first-timers'), ('solo-trekkers', 'solo-and-women'), ('solo-women', 'solo-and-women'),
 ('parents-and-seniors', 'seniors-and-groups'), ('families-and-children', 'families-and-kids'),
 ('body-and-fitness', 'am-i-fit-enough'), ('groups-and-friends', 'seniors-and-groups'),
 ('after-illness-or-injury', 'medical-conditions'),
 ('snow-reports', 'trail-conditions'), ('weekly-conditions', 'trail-conditions'), ('crowd-levels', 'trail-conditions'),
 ('road-and-access', 'trail-conditions'), ('season-wrap-ups', 'trip-reports'),
 ('guide-profiles', 'villages-and-people'), ('base-villages', 'villages-and-people'),
 ('village-life', 'local-village-stories'), ('culture-and-mythology', 'myths-and-temples'),
 ('wildlife-and-forests', 'wildlife-and-nature'), ('photo-essays', 'local-village-stories'),
 ('leave-no-trace', 'responsible-trekking'), ('overcrowding', 'responsible-trekking'),
 ('rules-and-regulation', 'responsible-trekking'), ('conservation', 'responsible-trekking'),
 ('local-economy', 'responsible-trekking'),
 ('trek-reports', 'trip-reports'), ('trekker-stories', 'trip-reports'),
 ('comeback-stories', 'trip-reports'), ('news-and-new-treks', 'news'),
-- Categories an admin had already added for the new layout before this ran (seen in production).
 ('your-trek', 'your-treks'), ('best-trek-and-comparisons', 'best-treks'), ('by-level', 'by-difficulty'),
 ('trek-vs-trek', 'comparisons'), ('trekking-gears-and-packing', 'gear-and-packing'),
 ('fitness-and-trek-prepration', 'fitness');

-- Any other existing category that already holds one of the new slugs (e.g. an admin-added "by-season") folds into it.
INSERT INTO moves
SELECT c.slug, c.slug FROM blog_categories c
WHERE c.slug IN (SELECT slug FROM seed UNION SELECT s.subs[i][1] FROM seed s, generate_subscripts(s.subs, 1) AS i)
ON CONFLICT (old_slug) DO NOTHING;

-- Free the V20 slugs (some come back, e.g. plan-your-trek). An admin may have renamed one; those keep their slug.
UPDATE blog_categories c SET slug = 'v20-' || c.slug FROM moves m WHERE c.slug = m.old_slug;

INSERT INTO blog_categories (parent_id, name, slug, position)
SELECT NULL, name, slug, pos FROM seed;

INSERT INTO blog_categories (parent_id, name, slug, position)
SELECT c.id, s.subs[i][2], s.subs[i][1], i
FROM seed s
JOIN blog_categories c ON c.slug = s.slug AND c.parent_id IS NULL
CROSS JOIN LATERAL generate_subscripts(s.subs, 1) AS i;

-- Posts follow their sub-category.
UPDATE blog_posts p SET category_id = n.id
FROM blog_categories o
JOIN moves m ON o.slug = 'v20-' || m.old_slug
JOIN blog_categories n ON n.slug = m.new_slug
WHERE p.category_id = o.id;

-- Sub-categories an admin added follow their category, after the seeded ones.
UPDATE blog_categories c SET parent_id = n.id, position = c.position + 100
FROM blog_categories o
JOIN moves m ON o.slug = 'v20-' || m.old_slug
JOIN blog_categories n ON n.slug = m.new_slug
WHERE c.parent_id = o.id AND c.slug NOT LIKE 'v20-%';

-- A V20 category an admin renamed isn't in `moves`: its sub-categories go under "Updates" rather than be lost.
UPDATE blog_categories c SET parent_id = (SELECT id FROM blog_categories WHERE slug = 'updates'), position = c.position + 100
WHERE c.slug NOT LIKE 'v20-%'
  AND c.parent_id IN (SELECT id FROM blog_categories WHERE parent_id IS NULL AND slug NOT IN (SELECT slug FROM seed));

-- Posts on a category about to go that nothing above moved (e.g. an admin-renamed one) land in "News", not lost.
UPDATE blog_posts p SET category_id = (SELECT id FROM blog_categories WHERE slug = 'news')
FROM blog_categories c
WHERE p.category_id = c.id
  AND ((c.slug LIKE 'v20-%' AND c.parent_id IS NOT NULL) OR (c.parent_id IS NULL AND c.slug NOT IN (SELECT slug FROM seed)));

-- Now empty: the V20 sub-categories, then every top-level category that isn't one of the new ten.
DELETE FROM blog_categories WHERE slug LIKE 'v20-%' AND parent_id IS NOT NULL;
DELETE FROM blog_categories WHERE parent_id IS NULL AND slug NOT IN (SELECT slug FROM seed);

-- Posts and categories share one slug space (§7.19): a post that held one of the new slugs gives it up.
UPDATE blog_posts p SET slug = p.slug || '-article'
WHERE EXISTS (SELECT 1 FROM blog_categories c WHERE c.slug = p.slug);
DELETE FROM blog_slug_redirects r WHERE EXISTS (SELECT 1 FROM blog_categories c WHERE c.slug = r.old_slug);

DROP TABLE moves;
DROP TABLE seed;
