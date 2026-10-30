package dannypx.foe.handler.logic;

import dannypx.foe.config.Configs;
import dannypx.foe.item.PetTagObject;

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
 * Pet price table from a Google Sheet: rating bands down the side ("<70%", "70-75%", "93%"),
 * rarities across the top (Common … Mythic), prices in the cells, plus optional extra columns
 * (e.g. "Max Scale/Luck") and a notes column. Downloaded as CSV in the background and refreshed
 * every 10 minutes.
 */
public class PetPriceSheetHandler {
    private static PetPriceSheetHandler INSTANCE = new PetPriceSheetHandler();

    private static final long REFRESH_MS = 10L * 60L * 1000L;
    public static final List<String> RARITIES = List.of("common", "rare", "epic", "legendary", "mythic");
    private static final Pattern RANGE = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*-\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern BELOW = Pattern.compile("<\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern EXACT = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*%?\\s*$");

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final AtomicBoolean loading = new AtomicBoolean(false);

    private volatile Table table = Table.EMPTY;
    private volatile long lastAttempt = 0L;
    private volatile long lastLoaded = 0L;
    private volatile String loadedFrom = "";
    private volatile String lastError = "";

    /// headers: all column headers; rows: all non-empty rows (first cell = rating band).
    public record Table(List<String> headers, List<List<String>> rows, int notesColumn) {
        static final Table EMPTY = new Table(List.of(), List.of(), -1);
    }

    public static PetPriceSheetHandler instance() {
        if (INSTANCE == null) {
            INSTANCE = new PetPriceSheetHandler();
        }
        return INSTANCE;
    }

    public Table getTable() {
        return table;
    }

    public String getLastError() {
        return lastError;
    }

    public long getLastLoaded() {
        return lastLoaded;
    }

    public boolean isLoading() {
        return loading.get();
    }

    public void tick() {
        String url = ContestBaitSheetHandler.toCsvUrl(Configs.mainConfig.petPriceSheetUrl.get());
        if (url == null) return;
        long now = System.currentTimeMillis();
        if ((!url.equals(loadedFrom) || now - lastAttempt > REFRESH_MS)) refresh(url);
    }

    /// Download now (the menu's Refresh button).
    public void refreshNow() {
        String url = ContestBaitSheetHandler.toCsvUrl(Configs.mainConfig.petPriceSheetUrl.get());
        if (url != null) refresh(url);
    }

    private void refresh(String url) {
        if (!loading.compareAndSet(false, true)) return;
        lastAttempt = System.currentTimeMillis();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "FishOnMC-Extras")
                .GET()
                .build();
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            try {
                if (error != null) {
                    lastError = "Couldn't reach the price sheet";
                } else if (response.statusCode() != 200) {
                    lastError = "Price sheet returned " + response.statusCode() + " (is it shared?)";
                } else {
                    Table parsed = parse(response.body());
                    if (parsed.rows().isEmpty()) {
                        lastError = "Price sheet has no rows";
                    } else {
                        table = parsed;
                        loadedFrom = url;
                        lastLoaded = System.currentTimeMillis();
                        lastError = "";
                    }
                }
            } catch (RuntimeException e) {
                lastError = "Couldn't read the price sheet";
            } finally {
                loading.set(false);
            }
        });
    }

    static Table parse(String csv) {
        List<List<String>> all = ContestBaitSheetHandler.readCsv(csv);
        if (all.isEmpty()) return Table.EMPTY;

        List<String> headers = new ArrayList<>();
        for (String h : all.getFirst()) headers.add(h.trim());
        // An unnamed last column with text in it holds notes.
        int notes = -1;
        for (int i = headers.size() - 1; i > 0; i--) {
            if (headers.get(i).isEmpty()) {
                notes = i;
                headers.set(i, "Notes");
                break;
            }
        }
        // Drop trailing columns that have no header and no data.
        int lastUsed = 0;
        for (int i = 0; i < headers.size(); i++) if (!headers.get(i).isEmpty()) lastUsed = i;
        List<String> trimmedHeaders = new ArrayList<>(headers.subList(0, lastUsed + 1));

        List<List<String>> rows = new ArrayList<>();
        for (int r = 1; r < all.size(); r++) {
            List<String> cells = all.get(r);
            if (cells.isEmpty() || cells.getFirst().isBlank()) continue;
            List<String> row = new ArrayList<>();
            for (int c = 0; c < trimmedHeaders.size(); c++) row.add(c < cells.size() ? cells.get(c).trim() : "");
            rows.add(row);
        }
        if (notes > lastUsed) notes = -1;
        return new Table(trimmedHeaders, rows, notes);
    }

    /// Column index for a rarity ("mythical" and "mythic" both work), or -1.
    public int columnFor(String rarity) {
        String key = rarity == null ? "" : rarity.toLowerCase(Locale.ROOT);
        if (key.startsWith("mythic")) key = "mythic";
        List<String> headers = table.headers();
        for (int i = 1; i < headers.size(); i++) {
            if (headers.get(i).toLowerCase(Locale.ROOT).startsWith(key) && !key.isEmpty()) return i;
        }
        return -1;
    }

    /// Row index whose rating band contains this rating (0–100), or -1. The first matching band wins.
    public int rowFor(double rating) {
        // An exact "93%" row beats a range; among ranges the one starting highest wins, so 75.5%
        // lands in "75-79%" rather than "70-75%".
        List<List<String>> rows = table.rows();
        int best = -1;
        double bestLow = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < rows.size(); i++) {
            String band = rows.get(i).getFirst();
            if (!bandContains(band, rating)) continue;
            if (EXACT.matcher(band).find()) return i;
            Matcher range = RANGE.matcher(band);
            double low = range.find() ? Double.parseDouble(range.group(1)) : Double.NEGATIVE_INFINITY;
            if (best < 0 || low > bestLow) {
                best = i;
                bestLow = low;
            }
        }
        return best;
    }

    static boolean bandContains(String band, double rating) {
        Matcher below = BELOW.matcher(band);
        if (below.find()) return rating < Double.parseDouble(below.group(1));
        Matcher range = RANGE.matcher(band);
        if (range.find()) {
            double low = Double.parseDouble(range.group(1));
            double high = Double.parseDouble(range.group(2));
            return rating >= low && rating < high + 1; // "75-79%" covers 75.0 up to 79.99
        }
        Matcher exact = EXACT.matcher(band);
        if (exact.find()) return Math.floor(rating) == Double.parseDouble(exact.group(1));
        return false;
    }

    /// Price cell for a pet, or null when the sheet has no match.
    public String priceFor(PetTagObject pet) {
        int column = columnFor(pet.getRarity());
        int row = rowFor(pet.getTotalPercent() * 100.0);
        if (column < 0 || row < 0) return null;
        String price = table.rows().get(row).get(column);
        return price.isBlank() ? null : price;
    }
}
