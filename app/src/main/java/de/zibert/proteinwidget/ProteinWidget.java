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

    // ---------- Themen ----------
    static class Theme {
        int top, bottom, glow, accentP, accentK, land;
        String name;
    }
    static Theme theme(Context ctx) {
        String t = ctx.getSharedPreferences("w", Context.MODE_PRIVATE).getString("theme", "outback");
        Theme th = new Theme();
        th.name = t;
        if ("riff".equals(t)) {
            th.top = Color.argb(235, 6, 28, 54);
            th.bottom = Color.argb(235, 8, 112, 128);
            th.glow = Color.argb(70, 94, 234, 212);
            th.accentP = Color.rgb(251, 146, 120);   // Koralle
            th.accentK = Color.rgb(253, 224, 140);   // Sand
            th.land = Color.argb(60, 186, 255, 240);
        } else {
            th.top = Color.argb(235, 24, 20, 48);    // Nachthimmel
            th.bottom = Color.argb(235, 176, 64, 24); // Outback-Rot
            th.glow = Color.argb(110, 255, 170, 60);
            th.accentP = Color.rgb(251, 191, 36);    // Gold
            th.accentK = Color.rgb(94, 214, 220);    // Türkis
            th.land = Color.argb(230, 92, 30, 14);   // Uluru
        }
        return th;
    }

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
        Theme th = theme(ctx);
        for (int id : ids) {
            Bundle o = mgr.getAppWidgetOptions(id);
            int wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180);
            int hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180);
            if (wDp <= 0) wDp = 180;
            if (hDp <= 0) hDp = 180;
            float scale = Math.min(dens, 900f / Math.max(wDp, hDp));
            int W = Math.round(wDp * scale), H = Math.round(hDp * scale);
            String stand = loading ? "lädt …" : ctx.getSharedPreferences("w", Context.MODE_PRIVATE).getString("stand", "");
            Bitmap bmp = render(json, W, H, th, stand);
            RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
            v.setImageViewBitmap(R.id.bild, bmp);
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

    static void background(Canvas c, int W, int H, float s, Theme th) {
        RectF box = new RectF(0, 0, W, H);
        float rad = 22 * s;
        Path clip = new Path();
        clip.addRoundRect(box, rad, rad, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setShader(new LinearGradient(0, 0, 0, H, th.top, th.bottom, Shader.TileMode.CLAMP));
        c.drawRect(box, g);
        // Sonne / Lichtschein
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        float gx = W * 0.78f, gy = H * 0.9f, gr = Math.max(W, H) * 0.55f;
        glow.setShader(new RadialGradient(gx, gy, gr, th.glow, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawRect(box, glow);
        Paint land = new Paint(Paint.ANTI_ALIAS_FLAG);
        land.setColor(th.land);
        if ("riff".equals(th.name)) {
            land.setStyle(Paint.Style.STROKE);
            land.setStrokeWidth(2.2f * s);
            for (int k = 0; k < 3; k++) {
                Path w = new Path();
                float base = H - (10 + k * 9) * s;
                w.moveTo(0, base);
                for (float x = 0; x <= W; x += 4) {
                    w.lineTo(x, base + (float) Math.sin(x / (22 * s) + k) * 3 * s);
                }
                land.setAlpha(70 - k * 18);
                c.drawPath(w, land);
            }
        } else {
            // Uluru-Silhouette
            Path u = new Path();
            float bh = H - 4 * s;
            float x0 = W * 0.42f, x1 = W * 1.02f, top = H - 26 * s;
            u.moveTo(x0, bh + 6 * s);
            u.cubicTo(x0 + 8 * s, top + 6 * s, x0 + 18 * s, top, x0 + 40 * s, top);
            u.lineTo(x1 - 30 * s, top + 1 * s);
            u.cubicTo(x1 - 14 * s, top + 2 * s, x1 - 6 * s, top + 10 * s, x1, bh + 6 * s);
            u.close();
            c.drawPath(u, land);
            Paint ground = new Paint();
            ground.setColor(Color.argb(200, 60, 18, 8));
            c.drawRect(0, bh, W, H, ground);
            // Kreuz des Südens
            Paint star = new Paint(Paint.ANTI_ALIAS_FLAG);
            star.setColor(Color.argb(150, 255, 255, 255));
            float[][] sc = {{0.62f,0.06f},{0.66f,0.13f},{0.70f,0.075f},{0.645f,0.18f},{0.675f,0.105f}};
            float sz = 1.6f * s;
            for (int k = 0; k < sc.length; k++) {
                c.drawCircle(W * sc[k][0], H * sc[k][1] + 4 * s, k == 4 ? sz * 0.6f : sz, star);
            }
            // Känguru
            kangaroo(c, W * 0.08f, bh, 30 * s, Color.argb(235, 40, 12, 6));
        }
        c.restore();
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(1.2f * s);
        edge.setColor(Color.argb(40, 255, 255, 255));
        c.drawRoundRect(box, rad, rad, edge);
    }

    static void kangaroo(Canvas c, float x, float groundY, float size, int color) {
        float[][] pt = {
            {0,92},{20,82},{40,70},{48,55},{55,40},{62,28},{64,20},{62,4},{67,14},{70,4},{71,16},
            {80,19},{88,23},{90,27},{80,30},{71,34},{68,44},{77,50},{80,56},{73,54},{66,58},
            {70,74},{75,88},{84,95},{93,98},{62,100},{55,93},{47,86},{20,92},{2,97}
        };
        float k = size / 100f;
        float top = groundY - 100 * k;
        Path p = new Path();
        p.moveTo(x + pt[0][0] * k, top + pt[0][1] * k);
        for (int i = 1; i < pt.length; i++) p.lineTo(x + pt[i][0] * k, top + pt[i][1] * k);
        p.close();
        Paint pa = new Paint(Paint.ANTI_ALIAS_FLAG);
        pa.setColor(color);
        pa.setPathEffect(new CornerPathEffect(2.5f * k));
        c.drawPath(p, pa);
    }

    static Bitmap render(String json, int W, int H, Theme th, String stand) {
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float s = Math.min(W / 220f, H / 218f);
        background(c, W, H, s, th);

        float L = 18 * s, R = W - 18 * s;
        Paint small = text(MUTED, 9 * s, false);
        small.setLetterSpacing(0.1f);

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
            c.drawText(zuletzt.isEmpty() ? datum : datum + "  ·  " + zuletzt, R, y, sr);

            y += 30 * s;
            y = row(c, s, L, R, y, "PROTEIN", pH, pZ, " g", th.accentP, small);
            y += 28 * s;
            y = row(c, s, L, R, y, "KCAL", kH, kZ, "", th.accentK, small);

            // Noch übrig
            y += 20 * s;
            float restP = Math.max(0, pZ - pH), restK = Math.max(0, kZ - kH);
            String rest = (restP <= 0 && restK <= 0) ? "Alles geschafft 💪"
                : "Noch " + Math.round(restP) + " g Protein  ·  " + Math.round(restK) + " kcal";
            c.drawText(rest, L, y, text(TEXT, 10.5f * s, true));

            // Trennlinie
            y += 12 * s;
            Paint line = new Paint();
            line.setColor(Color.argb(45, 255, 255, 255));
            c.drawRect(L, y, R, y + Math.max(1.5f, 0.8f * s), line);

            // Ø 7 Tage + Serie
            y += 17 * s;
            JSONObject avg = d.optJSONObject("schnitt");
            if (avg != null) {
                c.drawText("Ø 7 TAGE", L, y, small);
                float ap = (float) avg.optDouble("p", 0), ak = (float) avg.optDouble("k", 0);
                Paint v = text(TEXT, 10.5f * s, true);
                v.setTextAlign(Paint.Align.RIGHT);
                String kt = Math.round(ak) + " kcal";
                v.setColor(ak >= kZ ? GREEN : th.accentK);
                c.drawText(kt, R, y, v);
                float kw = v.measureText(kt);
                Paint dot = new Paint(small);
                dot.setTextAlign(Paint.Align.RIGHT);
                c.drawText("  ·  ", R - kw, y, dot);
                float dw = dot.measureText("  ·  ");
                v.setColor(ap >= pZ ? GREEN : th.accentP);
                c.drawText(Math.round(ap) + " g", R - kw - dw, y, v);
            }
            int serie = d.optInt("serie", 0);
            if (serie > 0 && y + 20 * s < H - 14 * s) {
                y += 19 * s;
                c.drawText("🔥 " + serie + (serie == 1 ? " Tag" : " Tage") + " Protein-Ziel in Folge",
                    L, y, text(TEXT, 9.5f * s, false));
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
        Paint big = text(col, 17 * s, true);
        Paint rest = text(MUTED, 10 * s, false);
        String r = " / " + Math.round(goal) + unit;
        float rw = rest.measureText(r);
        c.drawText(r, R - rw, y, rest);
        String v = String.valueOf(Math.round(val));
        c.drawText(v, R - rw - big.measureText(v), y, big);
        float by = y + 8 * s;
        float bh = 9 * s;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(TRACK);
        c.drawRoundRect(new RectF(L, by, R, by + bh), bh / 2, bh / 2, p);
        float end = L + (R - L) * clamp(val / goal);
        int light = Color.argb(255, Math.min(255, Color.red(col) + 40),
            Math.min(255, Color.green(col) + 40), Math.min(255, Color.blue(col) + 40));
        p.setShader(new LinearGradient(L, 0, end, 0, col, light, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(L, by, end, by + bh), bh / 2, bh / 2, p);
        return by + bh;
    }
}
