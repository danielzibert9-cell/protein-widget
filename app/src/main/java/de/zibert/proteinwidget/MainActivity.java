package de.zibert.proteinwidget;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setBackgroundColor(Color.rgb(17, 19, 24));
        l.setPadding(60, 60, 60, 60);

        TextView t = new TextView(this);
        t.setText("Protein-Widget\n\nWidget hinzufügen: lange auf den Startbildschirm drücken → Widgets → Protein-Widget.\nTippen aufs Widget lädt die Werte neu.\n\nDesign wählen:");
        t.setTextColor(Color.WHITE);
        t.setTextSize(17);
        t.setGravity(Gravity.CENTER);
        l.addView(t);

        l.addView(button("🌅  Outback-Sonnenuntergang", "outback"));
        l.addView(button("🌊  Küste / Riff", "riff"));
        setContentView(l);
        ProteinWidget.refresh(this);
    }

    Button button(String label, String theme) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setOnClickListener(v -> {
            getSharedPreferences("w", MODE_PRIVATE).edit().putString("theme", theme).apply();
            ProteinWidget.refresh(this);
            finish();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 30;
        btn.setLayoutParams(lp);
        return btn;
    }
}
