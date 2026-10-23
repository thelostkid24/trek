package com.sahyatri.catalog.service;

import com.sahyatri.catalog.dto.GuideProfile;
import com.sahyatri.guide.entity.CertificationType;
import com.sahyatri.guide.entity.GuideCertification;
import com.sahyatri.guide.entity.GuideProfileStat;
import com.sahyatri.guide.entity.GuideRouteKind;
import com.sahyatri.guide.entity.PledgeKind;
import com.sahyatri.guide.service.InstituteNames;
import com.sahyatri.review.dto.RatingSummary;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The guide page's stats row when an admin picked one (§7.21.8). Every value is worked out here on each read from the
 * guide's record, reviews and departures (law 8); a stat with nothing behind it is left out.
 */
final class GuideStats {

    /** Everything a stat can be worked out from. {@code recentWithUs}: completed departures in the last three years. */
    record Inputs(RatingSummary rating, long treksLed, Integer treksLast3y, long recentWithUs, Integer yearsLeading,
                  List<GuideProfile.TrekLed> routes, List<PledgeKind> pledges, List<GuideCertification> courses,
                  Integer highestAltitudeM, Integer returnRatePct, LocalDate today) {
    }

    private GuideStats() {
    }

    /** Null when none are picked: the page shows its default row. */
    static List<GuideProfile.Stat> stats(List<GuideProfileStat> picks, Inputs in) {
        if (picks.isEmpty()) {
            return null;
        }
        List<GuideProfile.Stat> out = new ArrayList<>();
        for (GuideProfileStat p : picks) {
            GuideProfile.Stat s = stat(p, in);
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    /** Short names of the BMC and AMC institutes, basic first, each once. */
    static List<String> trainedAt(List<GuideCertification> courses) {
        return courses.stream().map(c -> InstituteNames.shortName(c.getIssuer())).filter(Objects::nonNull)
                .distinct().toList();
    }

    private static GuideProfile.Stat stat(GuideProfileStat p, Inputs in) {
        return switch (p.getStatKey()) {
            case RATING -> in.rating().count() == 0 || in.rating().average() == null
                    ? new GuideProfile.Stat(p.getStatKey(), "New", "no reviews yet", null)
                    : new GuideProfile.Stat(p.getStatKey(), String.format(Locale.ROOT, "%.1f★", in.rating().average()),
                    plural(in.rating().count(), "review", "reviews"), null);
            case TREKS_LED -> in.treksLed() == 0 ? null : new GuideProfile.Stat(p.getStatKey(),
                    Long.toString(in.treksLed()), "treks led", plural(in.treksLed(), "trek", "treks"));
            case TREKS_LAST_3Y -> {
                if (in.treksLast3y() == null && in.recentWithUs() == 0) {
                    yield null;
                }
                long n = (in.treksLast3y() == null ? 0 : in.treksLast3y()) + in.recentWithUs();
                yield new GuideProfile.Stat(p.getStatKey(), Long.toString(n), "treks led in 3 years",
                        plural(n, "trek", "treks"));
            }
            case YEARS_LEADING -> in.yearsLeading() == null ? null : new GuideProfile.Stat(p.getStatKey(),
                    in.yearsLeading() + (in.yearsLeading() == 1 ? " yr" : " yrs"), "as main guide", null);
            case ROUTES_LED -> in.routes().isEmpty() ? null : new GuideProfile.Stat(p.getStatKey(),
                    Integer.toString(in.routes().size()), "routes led as main guide",
                    plural(in.routes().size(), "route", "routes"));
            case PEAK_CLIMBS -> {
                List<String> peaks = in.routes().stream().filter(r -> r.kind() == GuideRouteKind.PEAK)
                        .map(r -> peakName(r.name())).toList();
                yield peaks.isEmpty() ? null : new GuideProfile.Stat(p.getStatKey(), Integer.toString(peaks.size()),
                        (peaks.size() == 1 ? "peak climb led" : "peak climbs led") + " (" + String.join(", ", peaks)
                                + ")", null);
            }
            case EVACUATIONS -> in.pledges().contains(PledgeKind.NO_SERIOUS_INCIDENTS)
                    ? new GuideProfile.Stat(p.getStatKey(), "0", "evacuations on treks led", null) : null;
            case COURSES -> {
                List<String> at = trainedAt(in.courses());
                int n = in.courses().size();
                yield n == 0 ? null : new GuideProfile.Stat(p.getStatKey(), Integer.toString(n),
                        (n == 1 ? "mountaineering course" : "mountaineering courses")
                                + (at.isEmpty() ? "" : " (" + String.join(", ", at) + ")"), null);
            }
            case TOP_COURSE -> in.courses().stream()
                    .max((a, b) -> a.getType().compareTo(b.getType()))
                    .map(c -> {
                        String name = c.getType() == CertificationType.AMC ? "Advanced Mountaineering Course"
                                : "Basic Mountaineering Course";
                        String at = InstituteNames.shortName(c.getIssuer());
                        return new GuideProfile.Stat(p.getStatKey(), c.getType().name(),
                                at == null ? name : name + " (" + at + ")", null);
                    })
                    .orElse(null);
            case ROUTE_TIMES -> in.routes().stream()
                    .filter(r -> p.getRouteId().equals(r.routeId()) && r.times() != null)
                    .findFirst()
                    .map(r -> new GuideProfile.Stat(p.getStatKey(), r.times() + "×", r.name() + " led",
                            r.name() + " " + r.times() + "×"))
                    .orElse(null);
            case HIGHEST_PASS -> in.highestAltitudeM() == null ? null : new GuideProfile.Stat(p.getStatKey(),
                    feet(in.highestAltitudeM()), "highest pass led", null);
            case RETURN_RATE -> in.returnRatePct() == null ? null : new GuideProfile.Stat(p.getStatKey(),
                    in.returnRatePct() + "%", "trekkers come back", null);
        };
    }

    /** "Mt Friendship Peak" → "Friendship". */
    static String peakName(String name) {
        return name.replaceFirst("^(Mt\\.? |Mount )", "").replaceFirst(" Peak$", "").strip();
    }

    /** As the frontend's {@code feet}: to the nearest 5 ft, "15,000 ft". */
    static String feet(int metres) {
        long ft = Math.round(metres * 3.28084 / 5) * 5;
        return String.format(Locale.ROOT, "%,d ft", ft);
    }

    private static String plural(long n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }
}
