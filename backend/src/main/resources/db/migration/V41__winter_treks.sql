-- Dayara Bugyal, Brahmatal and Nag Tibba, listed and in the Winter filter (docs/TRD.md §7.9).
-- Copy is from the "trek-info" content pages. Each trek is skipped if an admin already made one with that slug,
-- and stays editable in the trek admin like any other.

DO $$
DECLARE
    t UUID := gen_random_uuid();
BEGIN
    IF EXISTS (SELECT 1 FROM tracks WHERE slug = 'dayara-bugyal') THEN
        RETURN;
    END IF;

    INSERT INTO tracks (id, slug, name, region, state, difficulty, duration_days, max_altitude_m, summary, description,
                        meeting_point, distance_km, base_altitude_m, highest_camp_m, stay, season_label, pickup_drop,
                        listed, created_at, updated_at)
    VALUES (t, 'dayara-bugyal', 'Dayara Bugyal', 'Raithal, Uttarkashi, Uttarakhand', 'Uttarakhand', 'EASY_MODERATE', 6, 3658,
            $q$Six days to one of the widest high meadows in Uttarakhand. Ten trekkers. A guide you picked yourself.$q$,
            $q$Dayara Bugyal is one of the largest high meadows in Uttarakhand. In summer it is miles of open green grass. In winter the same slopes turn into a wide snowfield with almost no trees in the way. From the top you see a long line of snow peaks, including Bandarpoonch, Srikanth and the Gangotri group.

The trail starts in Raithal, a village of old wooden houses above the Bhagirathi valley. You climb through oak, rhododendron and maple forest, camp at Gui and Chilapada, walk up to Dayara Top on the fourth day, and come down through a different forest to Nayata.

Dayara Bugyal is a good first Himalayan trek. The days are short, the climbs are steady, and the meadow gives you a big summit feeling without a steep, technical climb. But it is still a real mountain. Nights at camp go below freezing in winter, and summit day is long. Your mountain guide walks at the group's pace, and if he says the group turns back, the group turns back.$q$,
            'Dehradun', 21, 2164, 3170, 'Guest house, then tents',
            $q$Snow Dec–Mar · meadows Apr–Jun$q$, 'Dehradun to Dehradun', TRUE, now(), now());

    INSERT INTO track_seasons (track_id, season) VALUES (t, 'WINTER'), (t, 'SPRING'), (t, 'AUTUMN');

    INSERT INTO track_itinerary_days (id, track_id, day_number, summary, description, distance_km, high_altitude_m,
                                      end_altitude_m, hours_min, hours_max)
    VALUES
        (gen_random_uuid(), t, 1, 'Dehradun to Raithal',
         $q$185 km by road, 8 to 9 hours. You leave Dehradun early and drive into the Bhagirathi valley through Uttarkashi. The last stretch after Bhatwari climbs to Raithal. You reach in the afternoon, meet your guide and the rest of your batch, and your gear is checked that evening: shoes, jacket, gloves. Dinner and the night are in a guest house.$q$,
         NULL, NULL, 2164, NULL, NULL),
        (gen_random_uuid(), t, 2, 'Raithal to Gui',
         $q$You walk out of the village past terraced fields and climb into oak and rhododendron forest. The path is steady, with a few steep sections. Gui is a small clearing near a shepherd's hut with a view of the peaks. First night in tents.$q$,
         6, NULL, 2941, 4, 5),
        (gen_random_uuid(), t, 3, 'Gui to Chilapada',
         $q$A short day, on purpose. You leave the thick forest behind and reach the edge of the meadows. The short walk gives your body time to get used to the height before summit day. The afternoon is free: rest, walk around camp, and watch the sunset on the peaks.$q$,
         3, NULL, 3170, 2, 3),
        (gen_random_uuid(), t, 4, 'Chilapada to Dayara Top, down to Nayata',
         $q$The big day. You start early and climb across the open meadow to Dayara Top. On a clear day you see the peaks all the way around. In winter this is walking in snow, slow and steady. After time at the top you come down a different way, through forest, to camp at Nayata.$q$,
         10, 3658, NULL, 6, 7),
        (gen_random_uuid(), t, 5, 'Nayata to Raithal',
         $q$An easy downhill walk back to Raithal through forest and village fields. Lunch, a hot bath, and a slow evening in the village. Dinner and the night are in a guest house.$q$,
         4, NULL, 2164, 3, 4),
        (gen_random_uuid(), t, 6, 'Raithal to Dehradun',
         $q$185 km by road, 8 to 9 hours. You drive back down the valley and reach Dehradun by evening. Plan any onward train, bus or flight for the next morning.$q$,
         NULL, NULL, NULL, NULL, NULL);

    INSERT INTO trek_content_items (id, track_id, kind, position, body, created_at)
    SELECT gen_random_uuid(), t, 'INCLUDED', n - 1, body, now()
    FROM unnest(ARRAY[
        $q$All meals from Day 1 dinner to Day 5 lunch$q$,
        $q$Forest permits and camping fees (at Indian national rates)$q$,
        $q$Tents, sleeping bags and sleeping mats$q$,
        $q$Safety gear: rescue rope, harness, carabiners and pulleys$q$,
        $q$Mountain guide, cook, helpers and porters for group supplies$q$,
        $q$A mountain guide trained in mountaineering and wilderness first aid (NIM Uttarkashi)$q$,
        $q$One night in a guest house at Raithal/Natin$q$
    ]) WITH ORDINALITY AS x(body, n);

    INSERT INTO trek_content_items (id, track_id, kind, position, body, created_at)
    SELECT gen_random_uuid(), t, 'NOT_INCLUDED', n - 1, body, now()
    FROM unnest(ARRAY[
        $q$Carrying your personal backpack$q$,
        $q$Travel insurance$q$,
        $q$Personal expenses$q$,
        $q$Meals during the drive$q$,
        $q$Transport from Dehradun to Raithal and back$q$,
        $q$Anything not listed under What's included$q$
    ]) WITH ORDINALITY AS x(body, n);

    INSERT INTO trek_content_items (id, track_id, kind, position, title, body, created_at)
    SELECT gen_random_uuid(), t, 'FAQ', n - 1, title, body, now()
    FROM unnest(ARRAY[
        $q$Is the Dayara Bugyal trek good for beginners?$q$,
        $q$When is the best time for the Dayara Bugyal trek?$q$,
        $q$Will there be snow on Dayara Bugyal?$q$,
        $q$How do I reach Raithal for the Dayara Bugyal trek?$q$,
        $q$How cold does it get on the Dayara Bugyal trek?$q$,
        $q$Is there phone network on the Dayara Bugyal trek?$q$,
        $q$What is the minimum age for the Dayara Bugyal trek?$q$,
        $q$Dayara Bugyal or Kedarkantha: which should I choose?$q$
    ], ARRAY[
        $q$Yes. It is one of the easiest high-altitude treks in Uttarakhand, with short walking days and a gradual climb. You should be able to walk 5 to 6 hours a day with a daypack. Start walking or climbing stairs 4 weeks before the trek.$q$,
        $q$December to March for snow, April to June for green meadows and flowers, and September to November for the clearest mountain views. We do not run it in the July and August monsoon.$q$,
        $q$Snow usually covers the meadow from late December to March, but it changes every year. We will tell you what the trail looks like the week before you leave. We never promise snow.$q$,
        $q$Raithal is about 185 km from Dehradun, 8 to 9 hours by road through Uttarkashi. You can take our transport add-on from Dehradun, or reach Uttarkashi by bus and take a shared taxi to Raithal.$q$,
        $q$In winter, nights at camp can drop to minus 5 to minus 10°C. In spring and autumn they stay around 0 to 5°C. Bring layers: a thermal, a fleece and a down jacket.$q$,
        $q$There is some signal in Raithal. Above the village it is weak or missing on most of the trail. Tell your family and office before you leave.$q$,
        $q$Ask us. Children usually need to be 10 or older, and everyone under 18 treks with a parent or guardian.$q$,
        $q$Dayara Bugyal gives you wide, open meadows, a gentler climb and fewer people. Kedarkantha gives you a proper summit peak and a steeper summit day. If this is your first trek, Dayara Bugyal is the softer start.$q$
    ]) WITH ORDINALITY AS x(title, body, n);
END $$;

DO $$
DECLARE
    t UUID := gen_random_uuid();
BEGIN
    IF EXISTS (SELECT 1 FROM tracks WHERE slug = 'brahmatal') THEN
        RETURN;
    END IF;

    INSERT INTO tracks (id, slug, name, region, state, difficulty, duration_days, max_altitude_m, summary, description,
                        meeting_point, distance_km, base_altitude_m, highest_camp_m, stay, season_label, pickup_drop,
                        listed, created_at, updated_at)
    VALUES (t, 'brahmatal', 'Brahmatal', 'Lohajung, Chamoli, Uttarakhand', 'Uttarakhand', 'EASY_MODERATE', 6, 3840,
            $q$Six days from Dehradun. Ten trekkers, not twenty-five. You read your mountain guide's reviews before you pay.$q$,
            $q$Brahmatal is a winter trek with a view most treks only give you from the summit. From the second day, Mt Trishul and Mt Nanda Ghunti stand right in front of you, close and huge, and they stay with you almost all the way.

The trail starts in Lohajung, a small village in Chamoli. You climb through oak and rhododendron forest to Bekaltal, a quiet lake ringed by trees, then cross open ridges at Talindi Top and Jhandi Top to camp near Brahmatal lake. On summit day you climb Brahmatal Top for a wide circle of snow peaks, then walk back down to Lohajung.

Brahmatal suits first-timers who want real snow and big mountains without a steep, technical climb. But it is a full Himalayan trek. Winter nights at camp go well below freezing, and the walk back on Day 5 is long. Your mountain guide walks at the group's pace, and if he says the group turns back, the group turns back.$q$,
            'Dehradun', 28, 2332, 3399, 'Homestay, then tents · twin sharing',
            $q$Snow Dec–Mar · clear skies Oct–Nov$q$, 'Dehradun to Dehradun', TRUE, now(), now());

    INSERT INTO track_seasons (track_id, season) VALUES (t, 'WINTER'), (t, 'AUTUMN');

    INSERT INTO track_itinerary_days (id, track_id, day_number, summary, description, distance_km, high_altitude_m,
                                      end_altitude_m, hours_min, hours_max)
    VALUES
        (gen_random_uuid(), t, 1, 'Dehradun to Lohajung',
         $q$290 km by road, 11 to 12 hours. A long drive through the hills via Rishikesh, Devprayag and Karnaprayag, following the rivers most of the way. You reach Lohajung by evening, meet your guide and the rest of your batch, and your gear is checked: shoes, jacket, gloves. Dinner and the night are in a village homestay.$q$,
         NULL, NULL, 2332, NULL, NULL),
        (gen_random_uuid(), t, 2, 'Lohajung to Bekaltal',
         $q$You walk past village fields and climb into oak and rhododendron forest. The climb is steady and shaded. Camp is near Bekaltal, a small lake that often freezes in deep winter. First night in tents.$q$,
         6, NULL, 2743, 4, 5),
        (gen_random_uuid(), t, 3, 'Bekaltal to Brahmatal camp, via Talindi Top and Jhandi Top',
         $q$The best views of the trek begin today. You leave the forest and walk along open ridges with Trishul and Nanda Ghunti right in front of you. After Jhandi Top, a short descent brings you to camp, a few minutes' walk from Brahmatal lake.$q$,
         5, NULL, 3399, 4, 5),
        (gen_random_uuid(), t, 4, 'Brahmatal Top and back to camp',
         $q$Summit day. You start early and climb to Brahmatal Top for a 360-degree view of snow peaks. In winter you walk in snow, slow and steady. After time at the top you return to the same camp for a warm meal and an easy evening.$q$,
         6, 3840, 3399, 4, NULL),
        (gen_random_uuid(), t, 5, 'Brahmatal camp to Lohajung',
         $q$The longest walking day, mostly downhill. Take it at an even pace; your knees will thank you. You reach Lohajung by late afternoon. Dinner and the night are in the homestay.$q$,
         11, NULL, 2332, 7, 8),
        (gen_random_uuid(), t, 6, 'Lohajung to Dehradun',
         $q$290 km by road, 11 to 12 hours. An early start for the drive back. You reach Dehradun late in the evening, so plan any onward train, bus or flight for the next day.$q$,
         NULL, NULL, NULL, NULL, NULL);

    INSERT INTO trek_content_items (id, track_id, kind, position, body, created_at)
    SELECT gen_random_uuid(), t, 'INCLUDED', n - 1, body, now()
    FROM unnest(ARRAY[
        $q$Vegetarian and egg meals on the trek, from Day 1 dinner to Day 5 dinner$q$,
        $q$Forest permits and camping fees (at Indian national rates)$q$,
        $q$Tents on twin sharing, sleeping bags rated for the cold, and sleeping mats$q$,
        $q$Technical and safety equipment$q$,
        $q$Mountain guide, cook, helpers, porters and mules for group luggage$q$,
        $q$A certified mountaineer as your mountain guide$q$,
        $q$Two nights in a homestay (Day 1 and Day 5) on triple or quad sharing$q$
    ]) WITH ORDINALITY AS x(body, n);

    INSERT INTO trek_content_items (id, track_id, kind, position, body, created_at)
    SELECT gen_random_uuid(), t, 'NOT_INCLUDED', n - 1, body, now()
    FROM unnest(ARRAY[
        $q$Transport from Dehradun to Lohajung and back$q$,
        $q$Carrying your personal backpack (offloading)$q$,
        $q$Trek insurance$q$,
        $q$Meals during the drive$q$,
        $q$Personal expenses$q$,
        $q$Anything not listed under What's included$q$
    ]) WITH ORDINALITY AS x(body, n);

    INSERT INTO trek_content_items (id, track_id, kind, position, title, body, created_at)
    SELECT gen_random_uuid(), t, 'FAQ', n - 1, title, body, now()
    FROM unnest(ARRAY[
        $q$Is the Brahmatal trek good for beginners?$q$,
        $q$When is the best time for the Brahmatal trek?$q$,
        $q$Will there be snow on the Brahmatal trek?$q$,
        $q$How do I reach Lohajung for the Brahmatal trek?$q$,
        $q$How cold does it get on the Brahmatal trek?$q$,
        $q$Is there phone network on the Brahmatal trek?$q$,
        $q$What is the minimum age for the Brahmatal trek?$q$,
        $q$Brahmatal or Kedarkantha: which should I choose?$q$
    ], ARRAY[
        $q$Yes, if you are reasonably fit. The climb is gradual and there are no technical sections, but Day 5 is about 11 km of walking. You should be able to walk 6 to 7 hours with a daypack. Start walking or climbing stairs 4 to 6 weeks before the trek.$q$,
        $q$December to March for snow on the trail, and October and November for the clearest skies and sharpest mountain views.$q$,
        $q$Snow usually covers the upper trail from late December to March, but it changes every year. We will tell you what the trail looks like the week before you leave. We never promise snow.$q$,
        $q$Lohajung is about 290 km from Dehradun, 11 to 12 hours by road. You can take our transport add-on from Dehradun. From Kathgodam, shared jeeps run to Lohajung and take most of a day.$q$,
        $q$In winter, nights at camp can drop to minus 5 to minus 10°C. Bring layers: a thermal, a fleece and a down jacket, plus warm gloves and a woollen cap.$q$,
        $q$There is some signal in Lohajung. On most of the trail it is weak or missing. Tell your family and office before you leave.$q$,
        $q$Ask us. Children usually need to be 9 or older and trek with a parent or guardian. Trekkers over 60 should carry a doctor's fitness certificate.$q$,
        $q$Both are good winter treks for first-timers. Brahmatal gives you big, close views of Trishul and Nanda Ghunti, frozen lakes and quieter trails. Kedarkantha gives you a proper summit peak and a busier, more social trail.$q$
    ]) WITH ORDINALITY AS x(title, body, n);
END $$;

DO $$
DECLARE
    t UUID := gen_random_uuid();
BEGIN
    IF EXISTS (SELECT 1 FROM tracks WHERE slug = 'nag-tibba') THEN
        RETURN;
    END IF;

    INSERT INTO tracks (id, slug, name, region, state, difficulty, duration_days, max_altitude_m, summary, description,
                        meeting_point, distance_km, base_altitude_m, highest_camp_m, stay, season_label, pickup_drop,
                        listed, created_at, updated_at)
    VALUES (t, 'nag-tibba', 'Nag Tibba', 'Pantwari, Tehri Garhwal, Uttarakhand', 'Uttarakhand', 'EASY', 2, 3018,
            $q$A weekend in the Himalayas from Dehradun. Ten trekkers, not twenty-five. You read your mountain guide's reviews before you pay.$q$,
            $q$Nag Tibba is the highest peak in the Mussoorie range, and one of the few real Himalayan summits you can climb in a weekend. Leave Dehradun on Saturday morning, sleep in a tent under the stars, stand on the summit at sunrise on Sunday, and be back in Dehradun by evening.

The trail climbs from Pantwari village through fields and thick oak and rhododendron forest to a campsite below the summit. On a clear morning the top gives you a long line of snow peaks: Bandarpoonch, Swargarohini, the Gangotri group and, on the best days, Kedarnath.

Nag Tibba is the right first trek if you have never slept in a tent or walked in the mountains. It is short and safe, but it is still a real climb. Winter nights at camp go below freezing, and the summit push starts before dawn. Your mountain guide walks at the group's pace, and if he says the group turns back, the group turns back.$q$,
            'Dehradun', 14, NULL, 2408, 'Tents · twin sharing',
            $q$Weekend · 1 night, 2 days · snow Dec–Mar$q$, 'Dehradun to Dehradun', TRUE, now(), now());

    INSERT INTO track_seasons (track_id, season) VALUES (t, 'WINTER'), (t, 'SPRING'), (t, 'AUTUMN');

    INSERT INTO track_itinerary_days (id, track_id, day_number, summary, description, distance_km, high_altitude_m,
                                      end_altitude_m, hours_min, hours_max)
    VALUES
        (gen_random_uuid(), t, 1, 'Dehradun to Pantwari, trek to Nag Tibba camp',
         $q$3 to 4 hours by road, then about 6 km on foot. You leave Dehradun early and drive through Mussoorie and down to Pantwari. After lunch in the village, you meet your guide and start walking: first past fields and houses, then into oak and rhododendron forest. The climb is steady. You reach camp by late afternoon, in time for tea and the sunset. Dinner is at camp, and the night is in tents.$q$,
         6, NULL, 2408, 4, 5),
        (gen_random_uuid(), t, 2, 'Nag Tibba summit, back to Pantwari and Dehradun',
         $q$About 8 km on foot, then 3 to 4 hours by road. An early start with headlamps to reach the summit for sunrise and the view of the snow peaks. You come back to camp for breakfast, then walk down to Pantwari for lunch. The drive gets you back to Dehradun by evening.$q$,
         8, 3018, NULL, 5, 6);

    INSERT INTO trek_content_items (id, track_id, kind, position, body, created_at)
    SELECT gen_random_uuid(), t, 'INCLUDED', n - 1, body, now()
    FROM unnest(ARRAY[
        $q$Trek insurance (required for every trekker)$q$,
        $q$Camping as per the itinerary, on twin sharing, with men and women in separate tents$q$,
        $q$Vegetarian and egg meals: Day 1 lunch and dinner, Day 2 breakfast and lunch$q$,
        $q$A named mountain guide, trained in mountaineering and first aid$q$,
        $q$An experienced mountain cook$q$,
        $q$Local guides and support staff, depending on group size$q$,
        $q$A base manager who handles communication and sends extra help in an emergency$q$,
        $q$Sleeping bags, liners if needed, mattresses and utensils$q$,
        $q$All-season tents, plus kitchen, dining and toilet tents$q$,
        $q$Camping stools and walkie-talkies$q$,
        $q$Safety gear when needed: ropes, helmets, harnesses, microspikes and gaiters$q$,
        $q$Medical kit, oxygen, BP monitor and pulse oximeter$q$,
        $q$Mules or porters for the group's shared luggage$q$,
        $q$A place to leave extra luggage at base$q$,
        $q$All permits and entry fees (at Indian national rates)$q$,
        $q$Trek completion certificate$q$
    ]) WITH ORDINALITY AS x(body, n);

    INSERT INTO trek_content_items (id, track_id, kind, position, body, created_at)
    SELECT gen_random_uuid(), t, 'NOT_INCLUDED', n - 1, body, now()
    FROM unnest(ARRAY[
        $q$Transport from Dehradun to Pantwari and back (available as a non-AC tempo traveller or SUV)$q$,
        $q$Carrying your personal backpack (offloading)$q$,
        $q$Insurance for foreign nationals, NRIs and OCI card holders$q$,
        $q$Emergency evacuation and hospital costs$q$,
        $q$Meals during the drive$q$,
        $q$Personal expenses$q$,
        $q$Extra costs from delays beyond our control, such as landslides, road blocks or vehicle breakdown$q$,
        $q$On request, at extra cost: a satellite phone, a portable hyperbaric bag (Gamow or PAC bag) for altitude sickness, and an AED for a sudden cardiac emergency$q$,
        $q$Anything not listed under What's included$q$
    ]) WITH ORDINALITY AS x(body, n);

    INSERT INTO trek_content_items (id, track_id, kind, position, title, body, created_at)
    SELECT gen_random_uuid(), t, 'FAQ', n - 1, title, body, now()
    FROM unnest(ARRAY[
        $q$Is the Nag Tibba trek good for beginners?$q$,
        $q$Can I do the Nag Tibba trek in a weekend from Delhi?$q$,
        $q$When is the best time for the Nag Tibba trek?$q$,
        $q$Will there be snow on the Nag Tibba trek?$q$,
        $q$How cold does it get on the Nag Tibba trek?$q$,
        $q$Is there phone network on the Nag Tibba trek?$q$,
        $q$What is the minimum age for the Nag Tibba trek?$q$,
        $q$Is Nag Tibba a good warm-up for Kedarkantha?$q$
    ], ARRAY[
        $q$Yes. It is one of the best first treks in Uttarakhand. You should be able to walk 5 to 6 hours with a daypack. A few weeks of walking or climbing stairs before the trek will make it much easier.$q$,
        $q$Yes. Take an overnight bus or train from Delhi to Dehradun on Friday night, trek Saturday and Sunday, and take an overnight bus or train back on Sunday night.$q$,
        $q$December to March if you want a chance of snow, and April to June or September to November for green forest and clear views. We do not run it in the July and August monsoon.$q$,
        $q$Snow is common near the summit from late December to February, but it changes every year. We will tell you what the trail looks like the week before you leave. We never promise snow.$q$,
        $q$In winter, nights at camp can drop to minus 5°C or lower. Bring a thermal, a fleece, a down jacket, gloves and a woollen cap.$q$,
        $q$There is signal in Pantwari, and it is patchy on the trail and at camp. Tell your family and office before you leave.$q$,
        $q$Ask us. Children usually need to be 8 or older and trek with a parent or guardian.$q$,
        $q$Yes. It gives you a night in a tent, a pre-dawn summit push and a feel for the cold, all in one weekend. Many people do Nag Tibba first and Kedarkantha later in the season.$q$
    ]) WITH ORDINALITY AS x(title, body, n);
END $$;
