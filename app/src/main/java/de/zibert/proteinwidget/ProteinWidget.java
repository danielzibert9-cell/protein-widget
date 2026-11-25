package de.zibert.proteinwidget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.graphics.*;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class ProteinWidget extends AppWidgetProvider {

    static final String DATA_URL =
        "https://raw.githubusercontent.com/danielzibert9-cell/protein-widget/main/data.json";
    static final String ACTION_REFRESH = "de.zibert.proteinwidget.REFRESH";

    static final int GREEN = Color.rgb(74, 222, 128);
    static final int TEXT = Color.rgb(245, 240, 232);
    static final int MUTED = Color.argb(185, 245, 235, 225);
    static final int TRACK = Color.argb(45, 255, 255, 255);

    // ---------- Themen (wechseln täglich) ----------
    static final int[] BG = { R.drawable.bg01, R.drawable.bg02, R.drawable.bg03, R.drawable.bg04, R.drawable.bg05, R.drawable.bg06, R.drawable.bg07, R.drawable.bg08, R.drawable.bg09, R.drawable.bg10, R.drawable.bg11, R.drawable.bg12, R.drawable.bg13, R.drawable.bg14 };
    static final int[][] ACC = { {0xFFFBBF24, 0xFF5ED6DC}, {0xFFFB9278, 0xFFFDE08C}, {0xFFFFB86B, 0xFF8FD3FF}, {0xFFFFB347, 0xFF9DB8FF}, {0xFFF5D27A, 0xFFE8E2D0}, {0xFFE0B8FF, 0xFFFFFFFF}, {0xFFFFFFFF, 0xFFFFD66B}, {0xFFCFD9F2, 0xFF8FB8FF}, {0xFFFFCF6A, 0xFFF2A0FF}, {0xFFFFD690, 0xFF9FE0FF}, {0xFFFFC24A, 0xFFFFFFFF}, {0xFFFFC21A, 0xFFBFE0FF}, {0xFFFF3D6E, 0xFFC9CED6}, {0xFF4ADE80, 0xFFA7F3D0} };
    static int themeIndex() {
        Calendar c = Calendar.getInstance();
        long days = (c.getTimeInMillis() + c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)) / 86400000L;
        return (int) (days % BG.length);
    }
    static Bitmap bgCache; static int bgCacheIdx = -1;

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) { refresh(ctx); }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, Bundle o) {
        draw(ctx.getApplicationContext(), cached(ctx));
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) refresh(ctx);
    }

    static String cached(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences("w", Context.MODE_PRIVATE).getString("json", null);
    }

    static void scheduleMidnight(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 30);
        Intent i = new Intent(ctx, ProteinWidget.class).setAction(ACTION_REFRESH);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 1, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setAndAllowWhileIdle(AlarmManager.RTC, c.getTimeInMillis(), pi);
        // alle ~15 Minuten neue Werte holen, ohne Tippen
        PendingIntent poll = PendingIntent.getBroadcast(ctx, 2,
            new Intent(ctx, ProteinWidget.class).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            AlarmManager.INTERVAL_FIFTEEN_MINUTES, poll);
    }

    static void refresh(Context ctx) {
        final Context app = ctx.getApplicationContext();
        final SharedPreferences prefs = app.getSharedPreferences("w", Context.MODE_PRIVATE);
        scheduleMidnight(app);
        draw(app, prefs.getString("json", null));
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection)
                    new URL(DATA_URL + "?t=" + System.currentTimeMillis()).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                c.setUseCaches(false);
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                r.close();
                String json = sb.toString();
                new JSONObject(json);
                String now = new SimpleDateFormat("HH:mm", Locale.GERMANY).format(new Date());
                prefs.edit().putString("json", json).putString("stand", now).apply();
                draw(app, json);
            } catch (Exception e) {
                prefs.edit().putString("stand", "offline").apply();
                draw(app, prefs.getString("json", null));
            }
        }).start();
    }

    static void draw(Context ctx, String json) { draw(ctx, json, false); }

    static void draw(Context ctx, String json, boolean loading) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, ProteinWidget.class));
        float dens = ctx.getResources().getDisplayMetrics().density;
        int ti = themeIndex();
        if (bgCacheIdx != ti) { bgCache = android.graphics.BitmapFactory.decodeResource(ctx.getResources(), BG[ti]); bgCacheIdx = ti; }
        for (int id : ids) {
            Bundle o = mgr.getAppWidgetOptions(id);
            int wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180);
            int hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180);
            if (wDp <= 0) wDp = 180;
            if (hDp <= 0) hDp = 180;
            float scale = Math.min(dens, 900f / Math.max(wDp, hDp));
            int W = Math.round(wDp * scale), H = Math.round(hDp * scale);
            String stand = loading ? "lädt …" : ctx.getSharedPreferences("w", Context.MODE_PRIVATE).getString("stand", "");
            Bitmap bmp = render(json, W, H, ti, stand);
            RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
            v.setImageViewBitmap(R.id.bild, bmp);
            Intent i = new Intent(ctx, ProteinWidget.class).setAction(ACTION_REFRESH);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(R.id.root, pi);
            mgr.updateAppWidget(id, v);
        }
    }

    static float clamp(float x) { return Math.max(0.03f, Math.min(1f, x)); }

    static Paint text(int color, float size, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(size);
        p.setTypeface(Typeface.create(bold ? "sans-serif" : "sans-serif-medium",
            bold ? Typeface.BOLD : Typeface.NORMAL));
        return p;
    }

    static void shadow(Paint p, float s) { p.setShadowLayer(2.2f * s, 0.5f * s, 0.8f * s, Color.argb(170, 0, 0, 0)); }

    static void background(Canvas c, int W, int H, float s) {
        RectF box = new RectF(0, 0, W, H);
        float rad = 22 * s;
        Path clip = new Path();
        clip.addRoundRect(box, rad, rad, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        Bitmap bg = bgCache;
        if (bg != null) {
            float r = Math.max((float) W / bg.getWidth(), (float) H / bg.getHeight());
            float bw = bg.getWidth() * r, bh = bg.getHeight() * r;
            Paint pf = new Paint(Paint.FILTER_BITMAP_FLAG);
            c.drawBitmap(bg, null, new RectF((W - bw) / 2, (H - bh) / 2, (W + bw) / 2, (H + bh) / 2), pf);
        } else {
            c.drawColor(Color.rgb(20, 22, 28));
        }
        c.restore();
    }

    static Bitmap render(String json, int W, int H, int ti, String unused) {
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float s = Math.min(W / 220f, H / 218f);
        background(c, W, H, s);
        int accP = ACC[ti][0], accK = ACC[ti][1];

        float L = 18 * s, R = W - 18 * s;
        Paint small = text(MUTED, 10.5f * s, false);
        shadow(small, s);

        if (json == null) {
            c.drawText("LÄDT …  TIPPEN ZUM AKTUALISIEREN", L, 30 * s, small);
            return b;
        }
        try {
            JSONObject d = new JSONObject(json);
            String heute = new SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(new Date());
            boolean neuerTag = !heute.equals(d.optString("datumIso", heute));
            String datum = neuerTag
                ? new SimpleDateFormat("dd.MM.", Locale.GERMANY).format(new Date())
                : d.optString("datum", "");
            float pH = neuerTag ? 0 : (float) d.optDouble("proteinHeute", 0);
            float kH = neuerTag ? 0 : (float) d.optDouble("kcalHeute", 0);
            float pZ = (float) d.optDouble("proteinZiel", 140);
            float kZ = (float) d.optDouble("kcalZiel", 2500);

            float y = 26 * s;
            c.drawText("HEUTE", L, y, small);
            Paint sr = new Paint(small);
            sr.setTextAlign(Paint.Align.RIGHT);
            sr.setColor(TEXT);
            String zuletzt = neuerTag ? "" : d.optString("zuletzt", "");
            c.drawText(zuletzt.isEmpty() ? datum : datum + " · " + zuletzt, R, y, sr);

            y = 60 * s;
            y = row(c, s, L, R, y, "PROTEIN", pH, pZ, " g", accP, small);
            y += 32 * s;
            y = row(c, s, L, R, y, "KCAL", kH, kZ, "", accK, small);

            y += 23 * s;
            float restP = Math.max(0, pZ - pH), restK = Math.max(0, kZ - kH);
            String rest = (restP <= 0 && restK <= 0) ? "Alles geschafft 💪"
                : "Noch " + Math.round(restP) + " g  ·  " + Math.round(restK) + " kcal";
            Paint rp = text(TEXT, 13 * s, true); shadow(rp, s);
            c.drawText(rest, L, y, rp);

            y += 11 * s;
            Paint line = new Paint();
            line.setColor(Color.argb(70, 255, 255, 255));
            c.drawRect(L, y, R, y + Math.max(1.5f, 0.7f * s), line);

            y += 19 * s;
            JSONObject avg = d.optJSONObject("schnitt");
            if (avg != null) {
                c.drawText("Ø 7 TAGE", L, y, small);
                float ap = (float) avg.optDouble("p", 0), ak = (float) avg.optDouble("k", 0);
                Paint v = text(TEXT, 12.5f * s, true); shadow(v, s);
                v.setTextAlign(Paint.Align.RIGHT);
                String kt = Math.round(ak) + " kcal";
                v.setColor(ak >= kZ ? GREEN : accK);
                c.drawText(kt, R, y, v);
                float kw = v.measureText(kt);
                Paint dot = new Paint(small);
                dot.setTextAlign(Paint.Align.RIGHT);
                c.drawText("  ·  ", R - kw, y, dot);
                float dw = dot.measureText("  ·  ");
                v.setColor(ap >= pZ ? GREEN : accP);
                c.drawText(Math.round(ap) + " g", R - kw - dw, y, v);
            }
            int serie = d.optInt("serie", 0);
            if (serie > 0) {
                y += 20 * s;
                Paint sp = text(TEXT, 11.5f * s, false); shadow(sp, s);
                c.drawText("🔥 " + serie + (serie == 1 ? " Tag" : " Tage") + " Ziel in Folge", L, y, sp);
            }
        } catch (Exception e) {
            c.drawText("FEHLER BEIM LESEN", L, 30 * s, small);
        }
        return b;
    }

    static float row(Canvas c, float s, float L, float R, float y, String label,
                     float val, float goal, String unit, int accent, Paint small) {
        boolean done = val >= goal;
        int col = done ? GREEN : accent;
        c.drawText(done ? label + "  ✓" : label, L, y, small);
        Paint big = text(col, 21 * s, true); shadow(big, s);
        Paint rest = text(MUTED, 12 * s, false); shadow(rest, s);
        String r = " / " + Math.round(goal) + unit;
        float rw = rest.measureText(r);
        c.drawText(r, R - rw, y, rest);
        String v = String.valueOf(Math.round(val));
        c.drawText(v, R - rw - big.measureText(v), y, big);
        float by = y + 9 * s;
        float bh = 10 * s;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.argb(95, 0, 0, 0));
        c.drawRoundRect(new RectF(L, by, R, by + bh), bh / 2, bh / 2, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, 0.5f * s));
        p.setColor(Color.argb(45, 255, 255, 255));
        c.drawRoundRect(new RectF(L, by, R, by + bh), bh / 2, bh / 2, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(col);
        c.drawRoundRect(new RectF(L, by, L + (R - L) * clamp(val / goal), by + bh), bh / 2, bh / 2, p);
        return by + bh;
    }
}
