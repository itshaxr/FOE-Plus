package dannypx.foe.handler.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Your pet price table (O → Tools → Pet Prices, or U): what a pet sells for by its rating, as a Common's price (a low,
 * and a high when it's a range), times its rarity (×2 Rare, ×4 Epic, ×8 Legendary, ×16 Mythical), plus a bonus for
 * Max Luck and one for Max Scale. Every number can be changed in game; the starting ones are the community's prices
 * (they move with what people will pay). Saved in config/fishonmcextras/pet_prices.json.
 */
public final class PetPrices {
    private PetPrices() {}

    public static final String[] RARITIES = {"common", "rare", "epic", "legendary", "mythical"};

    /// One rating band: from..to (whole percents), and its Common price, low and high (high 0: just the low).
    public static final class Row {
        public int from, to;
        public long low, high;

        Row(int from, int to, long low, long high) {
            this.from = from;
            this.to = to;
            this.low = low;
            this.high = high;
        }

        public String label() {
            if (from == 0) return "≤" + to + "%";
            return from == to ? from + "%" : from + "-" + to + "%";
        }
    }

    static final class Saved {
        int version = 1;
        List<Row> rows = new ArrayList<>();
        long[] multipliers = {1, 2, 4, 8, 16};
        long maxLuckBonus = 50_000, maxScaleBonus = 50_000;
    }

    static Supplier<Path> FILE = () -> net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
            .resolve(dannypx.foe.FishOnMCExtras.MOD_ID).resolve("pet_prices.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Saved saved;

    /// The starting table. Below 60% in two bands; every percent from 60% up. 80% to 100% are the community's prices;
    /// 60% to 79% climb smoothly from 5K towards 80%'s 125K. Package-private for tests.
    static Saved defaults() {
        Saved s = new Saved();
        s.rows.add(new Row(0, 20, 100_000, 150_000));
        s.rows.add(new Row(21, 59, 100, 3_000));
        long[] climb = {5_000, 6_000, 7_000, 8_000, 9_500, 11_000, 13_000, 15_000, 18_000, 21_000,
                25_000, 30_000, 35_000, 40_000, 47_500, 55_000, 65_000, 75_000, 87_500, 100_000};
        for (int p = 60; p <= 79; p++) s.rows.add(new Row(p, p, climb[p - 60], 0));
        long[] top = {125_000, 150_000, 175_000, 200_000, 225_000, 250_000, 275_000, 325_000, 375_000, 425_000,
                500_000, 550_000, 600_000, 650_000, 700_000, 750_000, 800_000, 850_000, 950_000, 1_200_000, 5_000_000};
        for (int p = 80; p <= 100; p++) s.rows.add(new Row(p, p, top[p - 80], p == 97 ? 900_000 : p == 100 ? 6_000_000 : 0));
        return s;
    }

    private static Saved saved() {
        if (saved != null) return saved;
        saved = defaults();
        try {
            Path f = FILE.get();
            if (f != null && Files.exists(f)) {
                Saved s = GSON.fromJson(Files.readString(f), Saved.class);
                if (s != null && s.rows != null && !s.rows.isEmpty()) {
                    if (s.multipliers == null || s.multipliers.length != RARITIES.length) s.multipliers = defaults().multipliers;
                    saved = s;
                }
            }
        } catch (Throwable ignored) {
            // the defaults then
        }
        return saved;
    }

    public static void save() {
        try {
            Path f = FILE.get();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(saved()));
        } catch (Throwable ignored) {
            // kept for this session
        }
    }

    public static List<Row> rows() {
        return saved().rows;
    }

    public static long multiplier(int rarity) {
        return saved().multipliers[rarity];
    }

    public static void setMultiplier(int rarity, long value) {
        saved().multipliers[rarity] = value;
    }

    public static long maxLuckBonus() { return saved().maxLuckBonus; }
    public static long maxScaleBonus() { return saved().maxScaleBonus; }
    public static void setMaxLuckBonus(long v) { saved().maxLuckBonus = v; }
    public static void setMaxScaleBonus(long v) { saved().maxScaleBonus = v; }

    public static void reset() {
        saved = defaults();
        save();
    }

    /// The band a rating (in percent, 82.6) falls in (rounded to a whole percent, as the server shows it), or -1.
    public static int rowFor(double percent) {
        // rounded as the server shows it: an 82.6% pet reads "83%" in its tooltip, and is priced as 83%
        int p = (int) Math.round(percent);
        List<Row> rows = rows();
        for (int i = 0; i < rows.size(); i++) if (p >= rows.get(i).from && p <= rows.get(i).to) return i;
        return -1;
    }

    /// The rarity's column ("legendary" → 3), or -1.
    public static int rarityIndex(String rarity) {
        if (rarity == null) return -1;
        String r = rarity.toLowerCase(Locale.ROOT);
        if (r.startsWith("mythic")) r = "mythical";
        for (int i = 0; i < RARITIES.length; i++) if (RARITIES[i].equals(r)) return i;
        return -1;
    }

    /// {low, high} for a band and rarity, with the Max Luck / Max Scale bonuses (high equals low when it's no range).
    public static long[] price(int row, int rarity, boolean maxLuck, boolean maxScale) {
        Row r = rows().get(row);
        long m = multiplier(rarity);
        long bonus = (maxLuck ? maxLuckBonus() : 0) + (maxScale ? maxScaleBonus() : 0);
        long low = r.low * m + bonus;
        long high = (r.high > 0 ? r.high : r.low) * m + bonus;
        return new long[]{low, high};
    }

    /// 1500 → "1.5K", 1200000 → "1.2M", 950 → "950".
    public static String format(long v) {
        if (v >= 1_000_000_000L) return trim(v / 1_000_000_000.0) + "B";
        if (v >= 1_000_000) return trim(v / 1_000_000.0) + "M";
        if (v >= 1_000) return trim(v / 1_000.0) + "K";
        return String.valueOf(v);
    }

    /// "800K–3.6M", or one number.
    public static String format(long[] lowHigh) {
        return lowHigh[1] > lowHigh[0] ? format(lowHigh[0]) + "–" + format(lowHigh[1]) : format(lowHigh[0]);
    }

    private static String trim(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        return s.replaceAll("\\.?0+$", "");
    }

    /// "1.2m", "450k", "3,000", "5M" → the number; -1 for something that isn't one. Package-private for tests.
    public static long parse(String text) {
        if (text == null) return -1;
        String t = text.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("$", "");
        if (t.isEmpty()) return -1;
        double mult = 1;
        char last = t.charAt(t.length() - 1);
        if (last == 'k') mult = 1_000;
        else if (last == 'm') mult = 1_000_000;
        else if (last == 'b') mult = 1_000_000_000;
        if (mult > 1) t = t.substring(0, t.length() - 1);
        try {
            double v = Double.parseDouble(t);
            return v < 0 ? -1 : Math.round(v * mult);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
