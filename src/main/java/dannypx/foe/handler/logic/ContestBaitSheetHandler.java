package dannypx.foe.handler.logic;

import dannypx.foe.config.Configs;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-bait lookup for the Contest panel, read from a Google Sheet.
 * <p>
 * The sheet is downloaded as CSV (Google's export link, so no sign-in is needed as long as the
 * sheet is shared "anyone with the link") in the background when you join and every 10 minutes,
 * and kept in memory. Expected columns: Location, Family, Fish, Rarity, Habitat, Bait Strategy.
 * A contest like "Heaviest Perches" at "Great Lakes" is matched to the row whose location is
 * Great Lakes and whose family (or fish) is Perches.
 */
public class ContestBaitSheetHandler {
    private static ContestBaitSheetHandler INSTANCE = new ContestBaitSheetHandler();

    private static final long REFRESH_MS = 10L * 60L * 1000L;
    private static final Pattern SHEET_ID = Pattern.compile("/spreadsheets/d/([A-Za-z0-9_-]+)");
    private static final Pattern SHEET_GID = Pattern.compile("[?&#]gid=(\\d+)");
    /// Leading words of a contest type that aren't part of the fish name.
    private static final Pattern TYPE_PREFIX = Pattern.compile("^(heaviest|longest|biggest|largest|most|smallest|lightest|shortest)\\s+", Pattern.CASE_INSENSITIVE);

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final AtomicBoolean loading = new AtomicBoolean(false);

    private volatile List<Row> rows = List.of();
    private volatile long lastAttempt = 0L;
    private volatile String loadedFrom = "";
    private volatile String lastError = "";

    public static ContestBaitSheetHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new ContestBaitSheetHandler();
        }
        return INSTANCE;
    }

    public record Row(String location, String family, String fish, String strategy) {}

    public String getLastError() {
        return lastError;
    }

    public boolean hasData() {
        return !rows.isEmpty();
    }

    public void tick() {
        if (!Configs.hudConfig.showContestBestBait.get()) return;
        String url = toCsvUrl(Configs.hudConfig.contestBaitSheetUrl.get());
        if (url == null) return;
        long now = System.currentTimeMillis();
        boolean urlChanged = !url.equals(loadedFrom);
        if ((urlChanged || now - lastAttempt > REFRESH_MS) && loading.compareAndSet(false, true)) {
            lastAttempt = now;
            this.download(url);
        }
    }

    /// Turns any Google Sheets link (edit, share, export) into its CSV export link.
    public static String toCsvUrl(String link) {
        if (link == null || link.isBlank()) return null;
        Matcher id = SHEET_ID.matcher(link);
        if (!id.find()) return link.trim(); // already a direct CSV link
        String url = "https://docs.google.com/spreadsheets/d/" + id.group(1) + "/export?format=csv";
        Matcher gid = SHEET_GID.matcher(link);
        if (gid.find()) url += "&gid=" + gid.group(1);
        return url;
    }

    private void download(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "FishOnMC-Extras")
                .GET()
                .build();
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> {
                    try {
                        if (error != null) {
                            lastError = "Couldn't reach the bait sheet";
                        } else if (response.statusCode() != 200) {
                            lastError = "Bait sheet returned " + response.statusCode() + " (is it shared?)";
                        } else {
                            List<Row> parsed = parse(response.body());
                            if (parsed.isEmpty()) {
                                lastError = "Bait sheet has no rows";
                            } else {
                                rows = parsed;
                                loadedFrom = url;
                                lastError = "";
                            }
                        }
                    } catch (RuntimeException e) {
                        lastError = "Couldn't read the bait sheet";
                    } finally {
                        loading.set(false);
                    }
                });
    }

    static List<Row> parse(String csv) {
        List<Row> out = new ArrayList<>();
        List<List<String>> table = readCsv(csv);
        if (table.isEmpty()) return out;

        // Find columns by header name so reordering the sheet doesn't break anything.
        List<String> header = table.getFirst();
        int location = column(header, "location", 0);
        int family = column(header, "family", 1);
        int fish = column(header, "fish", 2);
        int strategy = column(header, "bait", 5);

        for (int i = 1; i < table.size(); i++) {
            List<String> cells = table.get(i);
            String loc = cell(cells, location);
            String bait = cell(cells, strategy);
            if (loc.isBlank() || bait.isBlank()) continue;
            out.add(new Row(loc, cell(cells, family), cell(cells, fish), bait));
        }
        return out;
    }

    private static int column(List<String> header, String name, int fallback) {
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).toLowerCase(Locale.ROOT).startsWith(name)) return i;
        }
        return fallback;
    }

    private static String cell(List<String> cells, int index) {
        return index >= 0 && index < cells.size() ? cells.get(index).trim() : "";
    }

    /// Small RFC 4180 reader: quoted fields, escaped quotes, commas and newlines inside quotes.
    public static List<List<String>> readCsv(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') i++;
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    /// "perches" -> "perch", "basses" -> "bass", "pikes" -> "pike".
    private static String singular(String word) {
        if (word.endsWith("ches") || word.endsWith("shes") || word.endsWith("sses")) return word.substring(0, word.length() - 2);
        if (word.endsWith("s") && word.length() > 3) return word.substring(0, word.length() - 1);
        return word;
    }

    private static boolean sameLocation(String a, String b) {
        String x = normalize(a);
        String y = normalize(b);
        if (x.isEmpty() || y.isEmpty()) return false;
        // The hub counts as Cypress Lake; "Toledo Bend" matches "Toledo Bend Reservoir".
        if (x.contains("spawn") || x.contains("hub")) x = "cypresslake";
        if (y.contains("spawn") || y.contains("hub")) y = "cypresslake";
        return x.contains(y) || y.contains(x);
    }

    private static boolean sameFish(String sheetName, String target) {
        String name = normalize(sheetName);
        if (name.isEmpty()) return false;
        return name.equals(target) || singular(name).equals(singular(target));
    }

    /// Rows for the current contest: family matches first, then fish-name matches.
    public List<Row> lookup(String contestLocation, String contestType) {
        List<Row> current = rows;
        if (current.isEmpty() || contestType.isBlank()) return List.of();
        String target = normalize(TYPE_PREFIX.matcher(contestType.trim()).replaceFirst(""));
        if (target.isEmpty()) return List.of();

        List<Row> byFamily = new ArrayList<>();
        List<Row> byFish = new ArrayList<>();
        for (Row row : current) {
            if (!sameLocation(row.location(), contestLocation)) continue;
            if (sameFish(row.family(), target)) byFamily.add(row);
            else if (sameFish(row.fish(), target)) byFish.add(row);
        }
        return byFamily.isEmpty() ? byFish : byFamily;
    }
}
