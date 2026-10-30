package com.payauction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Config {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("payauction-config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config INSTANCE = new Config();

    /** Dauer einer Auktion in Sekunden. */
    public int durationSeconds = 60;
    /** Eigene Zahlungen/Nachrichten mit dem eigenen Namen ignorieren. */
    public boolean ignoreOwnName = true;
    public boolean sounds = true;
    /** Meldung im eigenen Chat (nur lokal sichtbar), wenn jemand ueberboten wird. */
    public boolean announceLocal = true;
    /**
     * Regex-Muster fuer Pay-Nachrichten. Pflicht-Gruppen: (?<player>...) und (?<amount>...).
     * Die Muster beginnen mit ^, damit Spieler-Chatzeilen wie "<Name> X paid you $999" nicht greifen.
     */
    public List<String> patterns = defaultPatterns();

    public static List<String> defaultPatterns() {
        String amt = "\\$?\\s?(?<amount>[0-9][0-9.,]*(?:[kKmMbB](?![A-Za-z]))?)";
        String pl = "(?<player>[A-Za-z0-9_.]{3,16})";
        String start = "^\\s*(?!You\\s)";
        List<String> l = new ArrayList<>();
        l.add(start + pl + " (?:paid|sent|gave) you " + amt);
        l.add(start + pl + " (?:payt|pays|payed|paid|bezahlt|zahlt|bietet|bids)\\s+" + amt);
        l.add(start + pl + " hat dir " + amt + " (?:gezahlt|gesendet|überwiesen)");
        l.add("^\\s*You received " + amt + " from " + pl);
        return l;
    }

    public static Config get() {
        return INSTANCE;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Config c = GSON.fromJson(Files.readString(FILE), Config.class);
                if (c != null) {
                    if (c.patterns == null || c.patterns.isEmpty()) c.patterns = defaultPatterns();
                    INSTANCE = c;
                    return;
                }
            }
            Files.writeString(FILE, GSON.toJson(INSTANCE));
        } catch (Exception e) {
            System.err.println("[PayAuction] Config-Fehler: " + e);
        }
    }
}
