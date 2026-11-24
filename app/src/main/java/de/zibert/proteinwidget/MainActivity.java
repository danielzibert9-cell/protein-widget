package de.zibert.proteinwidget;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        TextView t = new TextView(this);
        t.setText("Protein-Widget ist installiert.\n\nLange auf den Startbildschirm drücken → Widgets → Protein-Widget auswählen.\n\nTippen aufs Widget aktualisiert die Werte.");
        t.setTextColor(Color.WHITE);
        t.setBackgroundColor(Color.rgb(17, 19, 24));
        t.setTextSize(18);
        t.setPadding(60, 60, 60, 60);
        t.setGravity(Gravity.CENTER);
        setContentView(t);
        ProteinWidget.refresh(this);
    }
}
