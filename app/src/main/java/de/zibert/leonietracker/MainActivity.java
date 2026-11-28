package de.zibert.leonietracker;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        handle(getIntent());
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(60, 80, 60, 60);
        l.setBackgroundColor(Color.rgb(42, 22, 48));
        TextView t = new TextView(this);
        String url = LeonieWidget.url(this);
        t.setText("💕 Leonies Tracker\n\n" + (url.isEmpty()
            ? "Noch nicht verbunden. Tippe auf der Einrichtungs-Seite auf „Widget verbinden“ oder füge die Adresse hier ein:"
            : "Verbunden ✓\n\nWidget hinzufügen: lange auf den Startbildschirm drücken → Widgets → Leonies Tracker (am besten 4×3 groß).\n\nLetzte Tage: zusätzlich „Tracker – Gestern/Vorgestern/vor 3 Tagen“ hinzufügen, lange drücken → „Stapel erstellen“, dann wischen.\n\nTippen auf „+250 ml“ trägt ein Glas Wasser ein.\n\nAdresse ändern:"));
        t.setTextColor(Color.WHITE);
        t.setTextSize(17);
        l.addView(t);
        EditText e = new EditText(this);
        e.setText(url);
        e.setTextColor(Color.WHITE);
        e.setHint("https://…/d/…");
        e.setHintTextColor(Color.GRAY);
        l.addView(e);
        Button s = new Button(this);
        s.setText("Speichern");
        s.setOnClickListener(v -> { LeonieWidget.setUrl(this, e.getText().toString().trim()); recreate(); });
        l.addView(s);
        setContentView(l);
        LeonieWidget.refresh(this);
    }

    @Override
    protected void onNewIntent(Intent i) { super.onNewIntent(i); handle(i); recreate(); }

    void handle(Intent i) {
        Uri d = i == null ? null : i.getData();
        if (d != null && "leonietracker".equals(d.getScheme())) {
            String u = d.getQueryParameter("u");
            if (u != null && u.startsWith("https://")) LeonieWidget.setUrl(this, u);
        }
    }
}
