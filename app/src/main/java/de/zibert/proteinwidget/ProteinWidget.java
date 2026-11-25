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
        final int W = 600, H = 470;
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

        float L = 16 * s, R = W - 16 * s;

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

            c.drawText("HEUTE", L, 24 * s, small);
            Paint sr = new Paint(small);
            sr.setTextAlign(Paint.Align.RIGHT);
            c.drawText(datum, R, 24 * s, sr);

            float y = 54 * s;
            y = row(c, s, L, R, y, "PROTEIN", pH, pZ, " g", small);
            y = row(c, s, L, R, y + 28 * s, "KCAL", kH, kZ, "", small);

            Paint line = new Paint();
            line.setColor(Color.argb(40, 255, 255, 255));
            float ly = y + 14 * s;
            c.drawRect(L, ly, R, ly + 1.5f, line);

            JSONObject avg = d.optJSONObject("schnitt");
            if (avg != null) {
                float ty = ly + 18 * s;
                c.drawText("Ø 7 TAGE", L, ty, small);
                float ap = (float) avg.optDouble("p", 0);
                float ak = (float) avg.optDouble("k", 0);
                Paint v = new Paint(Paint.ANTI_ALIAS_FLAG);
                v.setTextSize(11 * s);
                v.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                v.setTextAlign(Paint.Align.RIGHT);
                String kt = Math.round(ak) + " kcal";
                v.setColor(ak >= kZ ? GREEN : BLUE);
                c.drawText(kt, R, ty, v);
                float kw = v.measureText(kt);
                Paint dot = new Paint(small);
                dot.setTextAlign(Paint.Align.RIGHT);
                c.drawText("  ·  ", R - kw, ty, dot);
                float dw = dot.measureText("  ·  ");
                v.setColor(ap >= pZ ? GREEN : BLUE);
                c.drawText(Math.round(ap) + " g", R - kw - dw, ty, v);
            }
        } catch (Exception e) {
            c.drawText("FEHLER BEIM LESEN", L, 30 * s, small);
        }
        return b;
    }

    static float row(Canvas c, float s, float L, float R, float y, String label,
                     float val, float goal, String unit, Paint small) {
        boolean done = val >= goal;
        int col = done ? GREEN : BLUE;
        c.drawText(done ? label + "  ✓" : label, L, y, small);
        Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
        big.setColor(col);
        big.setTextSize(15 * s);
        big.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        Paint rest = new Paint(Paint.ANTI_ALIAS_FLAG);
        rest.setColor(GREY);
        rest.setTextSize(10 * s);
        String r = " / " + Math.round(goal) + unit;
        float rw = rest.measureText(r);
        c.drawText(r, R - rw, y, rest);
        String v = String.valueOf(Math.round(val));
        c.drawText(v, R - rw - big.measureText(v), y, big);
        float by = y + 7 * s;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(TRACK);
        float bh = 8 * s;
        c.drawRoundRect(new RectF(L, by, R, by + bh), bh / 2, bh / 2, p);
        p.setColor(col);
        c.drawRoundRect(new RectF(L, by, L + (R - L) * clamp(val / goal), by + bh), bh / 2, bh / 2, p);
        return by + bh;
    }
}
