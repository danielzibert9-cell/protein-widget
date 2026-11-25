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
        t.setText("Protein-Widget\n\nWidget hinzufügen: lange auf den Startbildschirm drücken → Widgets → Protein-Widget.\n\nFür die letzten Tage: zusätzlich die Widgets „Gestern“, „Vorgestern“ und „vor 3 Tagen“ hinzufügen, dann eins lang drücken → „Stapel erstellen“ und die anderen draufziehen. Danach einfach auf dem Widget nach links/rechts wischen.\n\nDas Design wechselt jeden Tag automatisch (14 Motive).\nTippen aufs Widget lädt die Werte sofort neu.");
        t.setTextColor(Color.WHITE);
        t.setBackgroundColor(Color.rgb(17, 19, 24));
        t.setTextSize(18);
        t.setPadding(60, 60, 60, 60);
        t.setGravity(Gravity.CENTER);
        setContentView(t);
        ProteinWidget.refresh(this);
    }
}
