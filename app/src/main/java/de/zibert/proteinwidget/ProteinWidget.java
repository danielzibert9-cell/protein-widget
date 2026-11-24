package de.zibert.proteinwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.*;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class ProteinWidget extends AppWidgetProvider {

    static final String DATA_URL =
        "https://raw.githubusercontent.com/danielzibert9-cell/protein-widget/main/data.json";
    static final String ACTION_REFRESH = "de.zibert.proteinwidget.REFRESH";

    static final int BG = Color.argb(240, 17, 19, 24);
    static final int GREEN = Color.rgb(74, 222, 128);
    static final int BLUE = Color.rgb(96, 165, 250);
    static final int GREY = Color.rgb(140, 145, 155);
    static final int TRACK = Color.argb(30, 255, 255, 255);

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        refresh(ctx);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) refresh(ctx);
    }

    static void refresh(Context ctx) {
        final Context app = ctx.getApplicationContext();
        final SharedPreferences prefs = app.getSharedPreferences("w", Context.MODE_PRIVATE);
        draw(app, prefs.getString("json", null), true);
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
                prefs.edit().putString("json", json).apply();
                draw(app, json, false);
            } catch (Exception e) {
                draw(app, prefs.getString("json", null), false);
            }
        }).start();
    }

    static void draw(Context ctx, String json, boolean loading) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, ProteinWidget.class));
        if (ids.length == 0) return;
        Bitmap bmp = render(json);
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        v.setImageViewBitmap(R.id.bild, bmp);
        Intent i = new Intent(ctx, ProteinWidget.class).setAction(ACTION_REFRESH);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.root, pi);
        mgr.updateAppWidget(ids, v);
    }

    static float clamp(float x) { return Math.max(0.04f, Math.min(1f, x)); }

    static Bitmap render(String json) {
        final int W = 600, H = 600;
        float s = W / 220f;
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(BG);
        c.drawRoundRect(new RectF(0, 0, W, H), 16 * s, 16 * s, p);

        Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
        small.setColor(GREY);
        small.setTextSize(9 * s);
        small.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        small.setLetterSpacing(0.08f);

        float L = 16 * s, R = W - 16 * s, BW = R - L;

        if (json == null) {
            c.drawText("LÄDT …  (tippen zum Aktualisieren)", L, 30 * s, small);
            return b;
        }
        try {
            JSONObject d = new JSONObject(json);
            String datum = d.optString("datum", "");
            float pH = (float) d.optDouble("proteinHeute", 0);
            float kH = (float) d.optDouble("kcalHeute", 0);
            float pZ = (float) d.optDouble("proteinZiel", 140);
            float kZ = (float) d.optDouble("kcalZiel", 2750);
            JSONArray woche = d.optJSONArray("woche");

            c.drawText("HEUTE", L, 24 * s, small);
            Paint sr = new Paint(small);
            sr.setTextAlign(Paint.Align.RIGHT);
            c.drawText(datum, R, 24 * s, sr);

            float y = 50 * s;
            y = row(c, s, L, R, y, "PROTEIN", pH, pZ, " g", GREEN, small);
            y = row(c, s, L, R, y + 10 * s, "KCAL", kH, kZ, "", BLUE, small);

            float ty = y + 18 * s;
            c.drawText("LETZTE 7 TAGE", L, ty, small);
            Paint lg = new Paint(sr);
            lg.setColor(BLUE);
            c.drawText("kcal", R, ty, lg);
            float kw = lg.measureText("kcal");
            lg.setColor(GREEN);
            c.drawText("Protein  ", R - kw, ty, lg);

            float top = ty + 10 * s, bottom = H - 22 * s, ch = bottom - top;
            Paint line = new Paint();
            line.setColor(Color.argb(45, 255, 255, 255));
            c.drawRect(L, top, R, top + 1.5f, line);
            if (woche != null) {
                int n = woche.length();
                float slot = BW / Math.max(n, 1);
                Paint day = new Paint(small);
                day.setTextAlign(Paint.Align.CENTER);
                Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
                float bw = Math.min(11 * s, slot * 0.32f);
                for (int i = 0; i < n; i++) {
                    JSONObject t = woche.getJSONObject(i);
                    float cx = L + slot * i + slot / 2;
                    float rp = clamp((float) t.optDouble("p", 0) / pZ);
                    float rk = clamp((float) t.optDouble("k", 0) / kZ);
                    bar.setColor(GREEN);
                    c.drawRoundRect(new RectF(cx - bw - 1.5f * s, bottom - ch * rp, cx - 1.5f * s, bottom), 3 * s, 3 * s, bar);
                    bar.setColor(BLUE);
                    c.drawRoundRect(new RectF(cx + 1.5f * s, bottom - ch * rk, cx + bw + 1.5f * s, bottom), 3 * s, 3 * s, bar);
                    c.drawText(t.optString("tag", ""), cx, bottom + 14 * s, day);
                }
            }
        } catch (Exception e) {
            c.drawText("FEHLER BEIM LESEN", L, 30 * s, small);
        }
        return b;
    }

    static float row(Canvas c, float s, float L, float R, float y, String label,
                     float val, float goal, String unit, int col, Paint small) {
        c.drawText(label, L, y, small);
        Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
        big.setColor(col);
        big.setTextSize(17 * s);
        big.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        Paint rest = new Paint(Paint.ANTI_ALIAS_FLAG);
        rest.setColor(GREY);
        rest.setTextSize(10 * s);
        String r = " / " + Math.round(goal) + unit;
        float rw = rest.measureText(r);
        c.drawText(r, R - rw, y, rest);
        String v = String.valueOf(Math.round(val));
        c.drawText(v, R - rw - big.measureText(v), y, big);
        float by = y + 10 * s;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(TRACK);
        float bh = 10 * s;
        c.drawRoundRect(new RectF(L, by, R, by + bh), bh / 2, bh / 2, p);
        p.setColor(col);
        c.drawRoundRect(new RectF(L, by, L + (R - L) * clamp(val / goal), by + bh), bh / 2, bh / 2, p);
        return by + bh;
    }
}
