package com.payauction;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class AuctionScreen extends Screen {
    private static final int GOLD = 0xFFD4AF37;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int PW = 280;

    private static String savedTitle = "";
    private static String savedStart = "1000";

    private AuctionManager.Phase shownPhase = AuctionManager.Phase.IDLE;
    private String error = "";
    private int px, py, ph;

    public AuctionScreen() {
        super(Text.literal("Pay Auction"));
    }

    // ================================================================ Aufbau

    @Override
    protected void init() {
        shownPhase = AuctionManager.phase();
        switch (shownPhase) {
            case IDLE -> ph = 182;
            case RUNNING -> ph = 226;
            default -> ph = 190;
        }
        px = (width - PW) / 2;
        py = Math.max(6, (height - ph) / 2);

        switch (shownPhase) {
            case IDLE -> initIdle();
            case RUNNING -> initRunning();
            default -> initEnded();
        }
    }

    private void initIdle() {
        int fx = px + 20, fw = PW - 40;

        TextFieldWidget titleField = new TextFieldWidget(textRenderer, fx, py + 46, fw, 20, Text.literal("Titel"));
        titleField.setMaxLength(40);
        titleField.setPlaceholder(Text.literal("z.B. Diamantschwert"));
        titleField.setText(savedTitle);
        titleField.setChangedListener(s -> savedTitle = s);
        addDrawableChild(titleField);

        TextFieldWidget startField = new TextFieldWidget(textRenderer, fx, py + 86, fw, 20, Text.literal("Startgebot"));
        startField.setMaxLength(14);
        startField.setPlaceholder(Text.literal("z.B. 1000"));
        startField.setText(savedStart);
        startField.setChangedListener(s -> savedStart = s);
        addDrawableChild(startField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Auktion starten").formatted(Formatting.GOLD), b -> startAuction())
                .dimensions(fx, py + 124, fw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Menü schließen"), b -> close())
                .dimensions(fx, py + 148, fw, 20).build());
    }

    private void startAuction() {
        String t = savedTitle.trim();
        double sb = Util.parseAmount(savedStart);
        if (t.isEmpty()) {
            error = "Bitte einen Namen eingeben.";
            return;
        }
        if (sb < 0) {
            error = "Ungültiges Startgebot (z.B. 1000, 5k, 2.5m).";
            return;
        }
        error = "";
        if (!AuctionManager.start(t, sb)) {
            error = "Es läuft bereits eine Auktion.";
        }
        clearAndInit();
    }

    private void initRunning() {
        int fx = px + 20, gap = 6;
        int bw = (PW - 40 - gap) / 2;
        int by = py + ph - 32;
        addDrawableChild(ButtonWidget.builder(Text.literal("Auktion abbrechen").formatted(Formatting.RED), b -> {
            AuctionManager.cancel();
            clearAndInit();
        }).dimensions(fx, by, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Menü schließen"), b -> close())
                .dimensions(fx + bw + gap, by, bw, 20).build());
    }

    private void initEnded() {
        int fx = px + 20, fw = PW - 40, gap = 6;
        int bw = (fw - gap) / 2;
        AuctionManager.Result r = AuctionManager.lastResult();
        boolean hasWinner = r != null && r.winner != null;

        if (hasWinner) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Zum Gewinner teleportieren").formatted(Formatting.GREEN),
                    b -> teleportToWinner()).dimensions(fx, py + ph - 56, fw, 20).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Neue Auktion"), b -> {
            AuctionManager.reset();
            clearAndInit();
        }).dimensions(fx, py + ph - 32, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Menü schließen"), b -> close())
                .dimensions(fx + bw + gap, py + ph - 32, bw, 20).build());
    }

    private void teleportToWinner() {
        AuctionManager.Result r = AuctionManager.lastResult();
        if (r == null || r.winner == null) return;
        // Name stammt aus dem Chat -> nur harmlose Zeichen zulassen
        if (!r.winner.matches("[A-Za-z0-9_.]{1,16}")) return;
        if (client != null && client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendChatCommand("tpa " + r.winner);
        }
        close();
    }

    // ================================================================ Live-Update

    @Override
    public void tick() {
        if (AuctionManager.phase() != shownPhase) {
            clearAndInit();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ================================================================ Zeichnen

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        // Dunkles Panel mit goldenem Rahmen
        ctx.fill(px - 2, py - 2, px + PW + 2, py + ph + 2, GOLD);
        ctx.fill(px, py, px + PW, py + ph, 0xFF14141C);
        ctx.fill(px, py, px + PW, py + 28, 0xFF1E1E2A);
        ctx.fill(px, py + 28, px + PW, py + 29, GOLD);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        switch (shownPhase) {
            case IDLE -> renderIdle(ctx);
            case RUNNING -> renderRunning(ctx);
            default -> renderEnded(ctx);
        }
    }

    private void txt(DrawContext ctx, String s, int x, int y, int color) {
        ctx.drawTextWithShadow(textRenderer, Text.literal(s), x, y, color);
    }

    private void ctr(DrawContext ctx, String s, int y, int color) {
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(s), px + PW / 2, y, color);
    }

    /** Skalierter, zentrierter Text. */
    private void big(DrawContext ctx, String s, int y, float scale, int color) {
        Text t = Text.literal(s);
        float w = textRenderer.getWidth(t) * scale;
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(px + PW / 2f - w / 2f, y);
        m.scale(scale, scale);
        ctx.drawTextWithShadow(textRenderer, t, 0, 0, color);
        m.popMatrix();
    }

    private String fit(String s, int maxW) {
        while (s.length() > 1 && textRenderer.getWidth(s) > maxW) s = s.substring(0, s.length() - 1);
        return s;
    }

    private void header(DrawContext ctx, String s, int color) {
        big(ctx, s, py + 8, 1.6f, color);
    }

    private void renderIdle(DrawContext ctx) {
        header(ctx, "PAY AUCTION", GOLD);
        txt(ctx, "Name des Items / der Auktion", px + 20, py + 35, GRAY);
        txt(ctx, "Startgebot (z.B. 1000, 5k, 2.5m)", px + 20, py + 75, GRAY);
        if (!error.isEmpty()) {
            ctr(ctx, error, py + 111, RED);
        } else {
            ctr(ctx, "Dauer: " + Config.get().durationSeconds + " Sekunden", py + 111, 0xFF777777);
        }
    }

    private void renderRunning(DrawContext ctx) {
        long ms = AuctionManager.remainingMs();
        long secs = (ms + 999) / 1000;
        boolean low = secs <= 10;

        header(ctx, fit(AuctionManager.title(), (int) ((PW - 20) / 1.6f)), GOLD);
        ctr(ctx, "Mindestgebot: " + Util.fmt(AuctionManager.startBid()), py + 34, GRAY);

        // Timer
        String time = String.format("%d:%02d", secs / 60, secs % 60);
        big(ctx, time, py + 46, 3.0f, low ? RED : GOLD);
        int barX = px + 20, barW = PW - 40, barY = py + 78;
        ctx.fill(barX, barY, barX + barW, barY + 4, 0xFF33333F);
        int filled = (int) (barW * (ms / (double) AuctionManager.totalMs()));
        ctx.fill(barX, barY, barX + filled, barY + 4, low ? RED : GOLD);

        // Hoechstbieter / Hoechstgebot
        AuctionManager.Bid hi = AuctionManager.highest();
        int colL = px + 20, colR = px + PW / 2 + 6;
        txt(ctx, "Höchstbieter", colL, py + 90, GRAY);
        txt(ctx, "Höchstgebot", colR, py + 90, GRAY);
        if (hi != null) {
            txt(ctx, fit(hi.player, PW / 2 - 30), colL, py + 102, GREEN);
            txt(ctx, Util.fmt(hi.amount), colR, py + 102, GREEN);
        } else {
            txt(ctx, "noch keiner", colL, py + 102, 0xFF777777);
            txt(ctx, "-", colR, py + 102, 0xFF777777);
        }

        // Alle Gebote (neueste zuerst)
        List<AuctionManager.Bid> bids = AuctionManager.bids();
        txt(ctx, "Gebote (" + bids.size() + ")", px + 20, py + 120, GRAY);
        ctx.fill(px + 20, py + 131, px + PW - 20, py + 132, 0xFF33333F);
        int shown = 0;
        for (int i = bids.size() - 1; i >= 0 && shown < 4; i--, shown++) {
            AuctionManager.Bid b = bids.get(i);
            int y = py + 136 + shown * 11;
            int col = (shown == 0) ? GREEN : GRAY;
            txt(ctx, fit(b.player, 130), px + 24, y, col);
            String amt = Util.fmt(b.amount);
            txt(ctx, amt, px + PW - 24 - textRenderer.getWidth(amt), y, col);
        }
        if (bids.isEmpty()) {
            ctr(ctx, "Warte auf Pay-Nachrichten im Chat...", py + 142, 0xFF777777);
        }
    }

    private void renderEnded(DrawContext ctx) {
        AuctionManager.Result r = AuctionManager.lastResult();
        header(ctx, "AUKTION BEENDET", RED);
        if (r == null) return;

        ctr(ctx, "Auktion: " + fit(r.title, PW - 90), py + 36, GOLD);
        if (r.winner != null) {
            ctr(ctx, "Gewinner", py + 56, GRAY);
            big(ctx, fit(r.winner, (int) ((PW - 20) / 2.4f)), py + 70, 2.4f, GREEN);
            ctr(ctx, "Höchstes Gebot", py + 100, GRAY);
            big(ctx, Util.fmt(r.amount), py + 112, 1.6f, GOLD);
        } else {
            big(ctx, "Kein Gewinner", py + 76, 2.0f, RED);
            ctr(ctx, "Mindestgebot " + Util.fmt(r.startBid) + " wurde nicht erreicht.", py + 104, GRAY);
        }
    }
}
