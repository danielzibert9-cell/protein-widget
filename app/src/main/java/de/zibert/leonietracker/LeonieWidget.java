package de.zibert.leonietracker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.Bundle;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class LeonieWidget extends AppWidgetProvider {
    static final String ACTION_REFRESH = "de.zibert.leonietracker.REFRESH";
    static final String ACTION_WASSER = "de.zibert.leonietracker.WASSER";
    static final int GREEN = Color.rgb(74, 222, 128);
    static final int TEXT = Color.rgb(250, 245, 240);
    static final int MUTED = Color.argb(225, 240, 228, 235);
    static final int[] BG = { R.drawable.bg01, R.drawable.bg02, R.drawable.bg03, R.drawable.bg04, R.drawable.bg05, R.drawable.bg06, R.drawable.bg07, R.drawable.bg08, R.drawable.bg09, R.drawable.bg10, R.drawable.bg11, R.drawable.bg12, R.drawable.bg13, R.drawable.bg14, R.drawable.bg15, R.drawable.bg16 };
    static final int[][] ACC = { {0xFFFFD27A, 0xFFFF9FC0, 0xFF8FE3FF}, {0xFFFFCF6A, 0xFFFFB3C8, 0xFF8FE3FF}, {0xFFFFD09A, 0xFFFF9FC0, 0xFF9FD8FF}, {0xFFFFD27A, 0xFFA8C4FF, 0xFF8FE3FF}, {0xFFFFD27A, 0xFFFFB3C8, 0xFF9FE8C8}, {0xFFFFB347, 0xFFFFB3C8, 0xFF9FE8FF}, {0xFFFFD27A, 0xFFFF9FC0, 0xFF9FD8FF}, {0xFFFFD27A, 0xFFFF9FC0, 0xFF9FE0FF}, {0xFFFFD27A, 0xFFFF9FC0, 0xFF8FE3FF}, {0xFFFFD27A, 0xFFFFB3C8, 0xFF9FE0FF}, {0xFFF5D27A, 0xFFFF9FC0, 0xFFBFE0FF}, {0xFFFFD27A, 0xFFFFD0E8, 0xFFA8E4FF}, {0xFFFFD27A, 0xFFFFB3C8, 0xFF8FE3FF}, {0xFFFFD27A, 0xFFFF9FC0, 0xFFBFF4FF}, {0xFFFFD27A, 0xFFFFD0E8, 0xFF9FE0FF}, {0xFFFFD27A, 0xFFFFB3C8, 0xFF9FE0FF} };
    static final Class<?>[] PROVIDER = { LeonieWidget.class, Gestern.class, Vorgestern.class, Vor3Tagen.class };
    public static class Gestern extends LeonieWidget {}
    public static class Vorgestern extends LeonieWidget {}
    public static class Vor3Tagen extends LeonieWidget {}

    static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences("w", Context.MODE_PRIVATE); }
    static String url(Context c) { return prefs(c).getString("url", ""); }
    static void setUrl(Context c, String u) { prefs(c).edit().putString("url", u).remove("json").apply(); }

    static int themeIndex(int off) {
        Calendar c = Calendar.getInstance();
        long days = (c.getTimeInMillis() + c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)) / 86400000L - off;
        return (int) (((days % BG.length) + BG.length) % BG.length);
    }

    @Override public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) { refresh(ctx); }
    @Override public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager m, int id, Bundle o) { draw(ctx.getApplicationContext()); }
    @Override public void onReceive(Context ctx, Intent i) {
        super.onReceive(ctx, i);
        if (ACTION_REFRESH.equals(i.getAction())) refresh(ctx);
        if (ACTION_WASSER.equals(i.getAction())) fetch(ctx, true);
    }

    static void schedule(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, 1); c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 30);
        am.setAndAllowWhileIdle(AlarmManager.RTC, c.getTimeInMillis(), bc(ctx, ACTION_REFRESH, 1));
        am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            AlarmManager.INTERVAL_FIFTEEN_MINUTES, bc(ctx, ACTION_REFRESH, 2));
    }
    static PendingIntent bc(Context ctx, String action, int code) {
        return PendingIntent.getBroadcast(ctx, code, new Intent(ctx, LeonieWidget.class).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void refresh(Context ctx) { schedule(ctx.getApplicationContext()); fetch(ctx, false); }

    static void fetch(Context ctx, boolean wasser) {
        final Context app = ctx.getApplicationContext();
        draw(app);
        final String u = url(app);
        if (u.isEmpty()) return;
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(wasser ? u + "/wasser" : u + "?t=" + System.currentTimeMillis()).openConnection();
                c.setConnectTimeout(10000); c.setReadTimeout(10000); c.setUseCaches(false);
                if (wasser) { c.setRequestMethod("POST"); c.setDoOutput(true); c.getOutputStream().close(); }
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = r.readLine()) != null) sb.append(line);
                r.close();
                new JSONObject(sb.toString());
                prefs(app).edit().putString("json", sb.toString()).apply();
            } catch (Exception e) { }
            draw(app);
        }).start();
    }

    static final HashMap<Integer, Bitmap> BGS = new HashMap<>();
    static Bitmap bg(Context ctx, int ti) {
        Bitmap b = BGS.get(ti);
        if (b == null) { if (BGS.size() > 4) BGS.clear(); b = BitmapFactory.decodeResource(ctx.getResources(), BG[ti]); BGS.put(ti, b); }
        return b;
    }

    static void draw(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        String json = prefs(ctx).getString("json", null);
        boolean ok = !url(ctx).isEmpty();
        float dens = ctx.getResources().getDisplayMetrics().density;
        for (int off = 0; off < PROVIDER.length; off++) {
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, PROVIDER[off]));
            if (ids.length == 0) continue;
            int ti = themeIndex(off);
            for (int id : ids) {
                Bundle o = mgr.getAppWidgetOptions(id);
                int wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300);
                int hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 250);
                if (wDp <= 0) wDp = 300; if (hDp <= 0) hDp = 250;
                float scale = Math.min(dens, 1000f / Math.max(wDp, hDp));
                int W = Math.round(wDp * scale), H = Math.round(hDp * scale);
                RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
                v.setImageViewBitmap(R.id.bild, render(ctx, ok ? json : null, ok, W, H, ti, off));
                PendingIntent ref = bc(ctx, ACTION_REFRESH, 0);
                v.setOnClickPendingIntent(R.id.tap1, ref);
                v.setOnClickPendingIntent(R.id.tap2, ref);
                v.setOnClickPendingIntent(R.id.wasser, off == 0 ? bc(ctx, ACTION_WASSER, 3) : ref);
                mgr.updateAppWidget(id, v);
            }
        }
    }

    static Paint text(int color, float size, boolean bold, float s) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color); p.setTextSize(size);
        p.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        p.setShadowLayer(2.2f * s, 0.5f * s, 0.8f * s, Color.argb(190, 0, 0, 0));
        return p;
    }
    static String z(double v) {
        String s = (Math.abs(v - Math.round(v)) < 0.05) ? String.valueOf(Math.round(v)) : String.format(Locale.GERMANY, "%.1f", v);
        return s;
    }
    static void bar(Canvas c, float x1, float y, float x2, float h, double frac, int col, float s) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.argb(110, 0, 0, 0));
        RectF r = new RectF(x1, y, x2, y + h);
        c.drawRoundRect(r, h / 2, h / 2, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1f, 0.5f * s)); p.setColor(Color.argb(60, 255, 255, 255));
        c.drawRoundRect(r, h / 2, h / 2, p);
        p.setStyle(Paint.Style.FILL); p.setColor(col);
        float w = (float) Math.max(h, (x2 - x1) * Math.min(1, Math.max(0, frac)));
        c.drawRoundRect(new RectF(x1, y, x1 + w, y + h), h / 2, h / 2, p);
    }

    static Bitmap render(Context ctx, String json, boolean ok, int W, int H, int ti, int off) {
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float s = Math.min(W / 300f, H / 250f);
        float rad = 22 * s;
        Path clip = new Path(); clip.addRoundRect(new RectF(0, 0, W, H), rad, rad, Path.Direction.CW);
        c.save(); c.clipPath(clip);
        Bitmap bg = bg(ctx, ti);
        float r = Math.max((float) W / bg.getWidth(), (float) H / bg.getHeight());
        float bw = bg.getWidth() * r, bh = bg.getHeight() * r;
        c.drawBitmap(bg, null, new RectF((W - bw) / 2, (H - bh) / 2, (W + bw) / 2, (H + bh) / 2), new Paint(Paint.FILTER_BITMAP_FLAG));
        c.restore();
        int[] acc = ACC[ti];
        float L = 16 * s, R = W - 16 * s;
        Paint small = text(MUTED, 9.5f * s, true, s);
        if (!ok) { c.drawText("Öffne die App „Leonies Tracker“", L, 30 * s, small); c.drawText("und verbinde sie einmal 💕", L, 46 * s, small); return b; }
        if (json == null) { c.drawText("LÄDT …", L, 30 * s, small); return b; }
        try {
            JSONObject d = new JSONObject(json);
            Calendar cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, -off);
            String iso = new SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(cal.getTime());
            String datum = new SimpleDateFormat("dd.MM.", Locale.GERMANY).format(cal.getTime());
            JSONObject zl = d.getJSONObject("ziele");
            JSONObject v = null; String zuletzt = "";
            if (iso.equals(d.optString("datumIso"))) { v = d.getJSONObject("heute"); if (off == 0) zuletzt = d.optString("zuletzt", ""); }
            else { JSONArray t = d.optJSONArray("tage"); if (t != null) for (int i = 0; i < t.length(); i++) if (iso.equals(t.getJSONObject(i).optString("datumIso"))) v = t.getJSONObject(i); }
            if (v == null) v = new JSONObject();
            String[] LAB = { "HEUTE", "GESTERN", "VORGESTERN", "VOR 3 TAGEN" };
            c.drawText(LAB[off], L, 20 * s, small);
            Paint hr = text(TEXT, 9.5f * s, true, s); hr.setTextAlign(Paint.Align.RIGHT);
            c.drawText(zuletzt.isEmpty() ? datum : datum + " · " + zuletzt, R, 20 * s, hr);

            float y = 44 * s;
            String[][] rows = { { "PROTEIN", "p", " g" }, { "KCAL", "k", "" }, { "WASSER", "w", " L" } };
            for (int i = 0; i < 3; i++) {
                double val = v.optDouble(rows[i][1], 0), goal = zl.optDouble(rows[i][1], 1);
                boolean liter = i == 2;
                boolean done = val >= goal;
                int col = done ? GREEN : acc[i];
                c.drawText(rows[i][0] + (done ? "  ✓" : ""), L, y, small);
                Paint gp = text(MUTED, 10.5f * s, false, s);
                String gs = " / " + (liter ? z(goal / 1000) : z(goal)) + rows[i][2];
                float gw = gp.measureText(gs);
                c.drawText(gs, R - gw, y, gp);
                Paint vp = text(col, 17 * s, true, s); vp.setTextAlign(Paint.Align.RIGHT);
                c.drawText(liter ? String.format(Locale.GERMANY, "%.1f", val / 1000) : z(val), R - gw, y, vp);
                bar(c, L, y + 5 * s, R, 7 * s, val / goal, col, s);
                y += 33 * s;
            }
            String[][] chips = { { "Gemüse", "gem", "g" }, { "Obst", "obst", "g" }, { "Eisen", "fe", "mg" }, { "Kalzium", "ca", "mg" }, { "B12", "b12", "µg" }, { "Zink", "zn", "mg" } };
            float cw = (R - L) / 3, y0 = y + 1 * s;
            for (int i = 0; i < chips.length; i++) {
                float cx = L + (i % 3) * cw, cy = y0 + (i / 3) * 29 * s;
                double val = v.optDouble(chips[i][1], 0), goal = zl.optDouble(chips[i][1], 1);
                boolean done = val >= goal;
                c.drawText(chips[i][0] + (done ? " ✓" : ""), cx, cy, text(MUTED, 8.5f * s, true, s));
                c.drawText(z(val) + " /" + z(goal) + " " + chips[i][2], cx, cy + 11 * s, text(done ? GREEN : TEXT, 9.5f * s, true, s));
                bar(c, cx, cy + 15 * s, cx + cw - 8 * s, 4.5f * s, val / goal, done ? GREEN : acc[i % 3], s);
            }
            float by = H * 0.78f;
            Paint line = new Paint(); line.setColor(Color.argb(70, 255, 255, 255));
            c.drawRect(L, by, R, by + Math.max(1.5f, 0.6f * s), line);
            double restP = Math.max(0, zl.optDouble("p", 0) - v.optDouble("p", 0));
            double restW = Math.max(0, zl.optDouble("w", 0) - v.optDouble("w", 0)) / 1000;
            String rest = off > 0 ? (restP <= 0 ? "Protein-Ziel geschafft 💪" : "Es fehlten " + z(restP) + " g Protein")
                : (restP <= 0 && restW <= 0 ? "Alles geschafft 💕" : "Noch " + z(restP) + " g · " + String.format(Locale.GERMANY, "%.1f", restW) + " L");
            c.drawText(rest, L, by + 19 * s, text(TEXT, 10.5f * s, true, s));
            int serie = d.optInt("serie", 0);
            if (serie > 0) c.drawText("💕 " + serie + (serie == 1 ? " Tag" : " Tage") + " Ziel in Folge", L, by + 35 * s, text(TEXT, 9.5f * s, false, s));
            if (off == 0) {
                float bx1 = W * 2f / 3 + 6 * s;
                Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG); bp.setColor(acc[2]); bp.setAlpha(235);
                c.drawRoundRect(new RectF(bx1, by + 8 * s, R, H - 8 * s), 12 * s, 12 * s, bp);
                Paint bt = new Paint(Paint.ANTI_ALIAS_FLAG); bt.setColor(Color.rgb(20, 30, 50)); bt.setTextSize(11 * s);
                bt.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); bt.setTextAlign(Paint.Align.CENTER);
                c.drawText("💧 +250 ml", (bx1 + R) / 2, (by + H) / 2 + 4 * s, bt);
            }
        } catch (Exception e) {
            c.drawText("FEHLER BEIM LESEN", L, 30 * s, small);
        }
        return b;
    }
}
