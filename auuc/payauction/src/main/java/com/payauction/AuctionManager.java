package com.payauction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Zustand und Logik der Auktion. Laeuft komplett auf dem Client-Thread. */
public final class AuctionManager {
    private AuctionManager() {
    }

    public enum Phase { IDLE, RUNNING, ENDED }

    public static class Bid {
        public String player;
        public double amount;
        public long time;
    }

    public static class Result {
        public String title;
        public double startBid;
        public String winner;      // null = kein Gewinner
        public double amount;
        public long time;
        public List<Bid> bids = new ArrayList<>();
    }

    private static final Path HISTORY_FILE = FabricLoader.getInstance().getConfigDir().resolve("payauction-history.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_HISTORY = 50;

    private static final List<Pattern> PATTERNS = new ArrayList<>();
    private static final List<Bid> BIDS = new ArrayList<>();
    private static final List<Result> HISTORY = new ArrayList<>();

    private static Phase phase = Phase.IDLE;
    private static String title = "";
    private static double startBid;
    private static long startTime;
    private static long endTime;
    private static Bid highest;
    private static Result lastResult;
    private static String lastRaw = "";
    private static long lastRawTime;

    // ------------------------------------------------------------ Init

    public static void init() {
        PATTERNS.clear();
        for (String r : Config.get().patterns) {
            try {
                PATTERNS.add(Pattern.compile(r, Pattern.CASE_INSENSITIVE));
            } catch (Exception e) {
                System.err.println("[PayAuction] Ungültiges Muster ignoriert: " + r);
            }
        }
        try {
            if (Files.exists(HISTORY_FILE)) {
                List<Result> l = GSON.fromJson(Files.readString(HISTORY_FILE), new TypeToken<List<Result>>() {}.getType());
                if (l != null) HISTORY.addAll(l);
            }
        } catch (Exception e) {
            System.err.println("[PayAuction] Verlauf nicht lesbar: " + e);
        }
    }

    // ------------------------------------------------------------ Zugriff fuer die GUI

    public static Phase phase() {
        return phase;
    }

    public static String title() {
        return title;
    }

    public static double startBid() {
        return startBid;
    }

    public static Bid highest() {
        return highest;
    }

    public static List<Bid> bids() {
        return Collections.unmodifiableList(BIDS);
    }

    public static Result lastResult() {
        return lastResult;
    }

    public static long remainingMs() {
        if (phase != Phase.RUNNING) return 0;
        return Math.max(0, endTime - System.currentTimeMillis());
    }

    public static long totalMs() {
        return Math.max(1, endTime - startTime);
    }

    // ------------------------------------------------------------ Steuerung

    public static boolean start(String auctionTitle, double minBid) {
        if (phase == Phase.RUNNING) return false; // nur eine Auktion gleichzeitig
        BIDS.clear();
        highest = null;
        lastResult = null;
        title = auctionTitle;
        startBid = minBid;
        startTime = System.currentTimeMillis();
        endTime = startTime + Math.max(5, Config.get().durationSeconds) * 1000L;
        lastRaw = "";
        phase = Phase.RUNNING;
        say(MinecraftClient.getInstance(), "Auktion gestartet: " + auctionTitle + " | Mindestgebot "
                + Util.fmt(minBid) + " | " + Config.get().durationSeconds + " s", Formatting.GOLD);
        return true;
    }

    public static void cancel() {
        if (phase != Phase.RUNNING) return;
        phase = Phase.IDLE;
        BIDS.clear();
        highest = null;
        say(MinecraftClient.getInstance(), "Auktion abgebrochen.", Formatting.RED);
    }

    public static void reset() {
        if (phase == Phase.RUNNING) return;
        phase = Phase.IDLE;
        BIDS.clear();
        highest = null;
    }

    /** Pro Client-Tick: prueft, ob die Zeit abgelaufen ist. */
    public static void tick(MinecraftClient c) {
        if (phase == Phase.RUNNING && System.currentTimeMillis() >= endTime) {
            finish(c);
        }
    }

    private static void finish(MinecraftClient c) {
        phase = Phase.ENDED;
        Result r = new Result();
        r.title = title;
        r.startBid = startBid;
        r.time = System.currentTimeMillis();
        r.bids = new ArrayList<>(BIDS);
        if (highest != null) {
            r.winner = highest.player;
            r.amount = highest.amount;
        }
        lastResult = r;
        HISTORY.add(r);
        while (HISTORY.size() > MAX_HISTORY) HISTORY.remove(0);
        try {
            Files.writeString(HISTORY_FILE, GSON.toJson(HISTORY));
        } catch (Exception e) {
            System.err.println("[PayAuction] Verlauf nicht speicherbar: " + e);
        }

        play(c, SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f);
        if (r.winner != null) {
            say(c, "Auktion beendet! Gewinner: " + r.winner + " mit " + Util.fmt(r.amount), Formatting.GREEN);
        } else {
            say(c, "Auktion beendet. Kein Gewinner.", Formatting.RED);
        }
        if (c.player != null && c.currentScreen == null) {
            c.setScreen(new AuctionScreen());
        }
    }

    // ------------------------------------------------------------ Chat-Analyse

    public static void onMessage(Text message, boolean overlay) {
        if (phase != Phase.RUNNING || overlay) return;
        long now = System.currentTimeMillis();
        if (now >= endTime) return;

        String s = message.getString();
        if (s.isEmpty() || s.length() > 300) return;

        // Doppelt zugestellte Nachricht direkt hintereinander ignorieren
        if (s.equals(lastRaw) && now - lastRawTime < 400) return;
        lastRaw = s;
        lastRawTime = now;

        for (Pattern p : PATTERNS) {
            Matcher m = p.matcher(s);
            if (!m.find()) continue;
            String player;
            double amount;
            try {
                player = m.group("player");
                amount = Util.parseAmount(m.group("amount"));
            } catch (IllegalArgumentException e) {
                continue; // Muster ohne die Pflicht-Gruppen
            }
            if (player == null || amount <= 0) continue;
            accept(MinecraftClient.getInstance(), player, amount, now);
            return;
        }
    }

    private static void accept(MinecraftClient c, String player, double amount, long now) {
        if (player.equalsIgnoreCase("you")) return;
        if (Config.get().ignoreOwnName && c.getSession() != null
                && player.equalsIgnoreCase(c.getSession().getUsername())) return;
        if (amount < startBid) return;                                  // Mindestgebot nicht erreicht
        if (highest != null && amount <= highest.amount) return;        // nur hoehere Gebote

        Bid previous = highest;
        Bid bid = new Bid();
        bid.player = player;
        bid.amount = amount;
        bid.time = now;
        BIDS.add(bid);
        highest = bid;

        play(c, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f);
        if (Config.get().announceLocal) {
            if (previous != null && !previous.player.equalsIgnoreCase(player)) {
                say(c, previous.player + " wurde überboten! Neues Höchstgebot: " + player + " mit "
                        + Util.fmt(amount), Formatting.YELLOW);
            } else {
                say(c, "Neues Höchstgebot: " + player + " mit " + Util.fmt(amount), Formatting.YELLOW);
            }
        }
    }

    // ------------------------------------------------------------ Hilfen

    private static void play(MinecraftClient c, SoundEvent ev, float pitch) {
        if (!Config.get().sounds || c == null) return;
        if (c.player != null) c.player.playSound(ev, 1.0f, pitch);
    }

    private static void say(MinecraftClient c, String msg, Formatting color) {
        if (c == null || c.player == null) return;
        c.player.sendMessage(Text.literal("[Auktion] ").formatted(Formatting.GOLD)
                .append(Text.literal(msg).formatted(color)), false);
    }
}
