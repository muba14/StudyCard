package com.studycard.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Notu görüntüleme ekranı; "Tekrar ettim" ile sonraki tekrar planlanır. */
public class NoteDetailActivity extends Activity {

    public static final String EXTRA_ID = "id";

    private long noteId;
    private Note note;

    private TextView tvSubject, tvTitle, tvNext, tvCount, tvContent, tvChartTitle;
    private View contentCard, imagesCard, chartCard;
    private LinearLayout imagesContainer;
    private ChartView chart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        Ui.edgeToEdge(this, findViewById(R.id.header), findViewById(R.id.bottomBar), null);

        tvSubject = findViewById(R.id.tvSubject);
        tvTitle = findViewById(R.id.tvTitle);
        tvNext = findViewById(R.id.tvNext);
        tvCount = findViewById(R.id.tvCount);
        tvContent = findViewById(R.id.tvContent);
        tvChartTitle = findViewById(R.id.tvChartTitle);
        contentCard = findViewById(R.id.contentCard);
        imagesCard = findViewById(R.id.imagesCard);
        chartCard = findViewById(R.id.chartCard);
        imagesContainer = findViewById(R.id.imagesContainer);
        chart = findViewById(R.id.chart);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnEdit).setOnClickListener(v ->
                startActivity(new Intent(this, EditNoteActivity.class)
                        .putExtra(EditNoteActivity.EXTRA_ID, noteId)));
        findViewById(R.id.btnDelete).setOnClickListener(v -> confirmDelete());
        findViewById(R.id.btnReviewed).setOnClickListener(v -> showReviewedDialog());
        findViewById(R.id.reviewCard).setOnClickListener(v -> showReviewedDialog());

        noteId = getIntent().getLongExtra(EXTRA_ID, 0);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        noteId = intent.getLongExtra(EXTRA_ID, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        note = NoteDb.get(this).get(noteId);
        if (note == null) {
            Toast.makeText(this, "Not bulunamadı", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        // Not açıldıysa bildirimi kapat
        ReminderScheduler.dismissNotification(this, noteId);
        bind();
    }

    private void bind() {
        tvSubject.setText(note.subject == null || note.subject.isEmpty() ? "Genel" : note.subject);
        tvTitle.setText(note.title);

        if (note.hasReminder()) {
            tvNext.setText(Ui.isOverdue(note.reviewAt) || note.reviewAt <= System.currentTimeMillis()
                    ? Ui.formatFriendly(note.reviewAt) + "  ·  zamanı geldi"
                    : Ui.formatFriendly(note.reviewAt));
        } else {
            tvNext.setText("Planlanmadı");
        }
        tvCount.setText(note.reviewCount == 0
                ? "Henüz tekrar etmedin · dokunarak planla"
                : "Bu notu " + note.reviewCount + " kez tekrar ettin");

        String content = note.content == null ? "" : note.content.trim();
        contentCard.setVisibility(content.isEmpty() ? View.GONE : View.VISIBLE);
        tvContent.setText(content);

        imagesContainer.removeAllViews();
        imagesCard.setVisibility(note.images.isEmpty() ? View.GONE : View.VISIBLE);
        int width = getResources().getDisplayMetrics().widthPixels;
        for (int i = 0; i < note.images.size(); i++) {
            final String name = note.images.get(i);
            ImageView iv = new ImageView(this);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setBackgroundResource(R.drawable.bg_thumb);
            iv.setClipToOutline(true);
            iv.setMinimumHeight(Ui.dp(this, 120));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.topMargin = Ui.dp(this, 10);
            iv.setLayoutParams(lp);
            iv.setOnClickListener(v -> ImageViewerDialog.show(this, name));
            imagesContainer.addView(iv);
            ThumbLoader.load(this, name, width, iv);
        }

        chartCard.setVisibility(note.hasChart() ? View.VISIBLE : View.GONE);
        if (note.hasChart()) {
            tvChartTitle.setText(note.chartTitle == null || note.chartTitle.isEmpty()
                    ? getString(R.string.section_chart) : note.chartTitle);
            chart.setChart(note.chartType, note.chartEntries);
        }
    }

    // ------------------------------------------------------------------

    private void showReviewedDialog() {
        final int suggested = Ui.suggestedDays(note.reviewCount);
        final int[] days = {suggested, 1, 3, 7, 14, 30};
        List<String> labels = new ArrayList<>();
        labels.add("★  Önerilen: " + Ui.daysLabel(suggested) + "  (aralıklı tekrar)");
        for (int i = 1; i < days.length; i++) labels.add(Ui.daysLabel(days[i]));
        labels.add("Özel tarih seç…");
        labels.add("Bir daha hatırlatma");

        new AlertDialog.Builder(this)
                .setTitle("Harika! Sonraki tekrar ne zaman?")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    long now = System.currentTimeMillis();
                    if (which < days.length) {
                        markReviewed(Ui.sameTimeDaysLater(note.reviewAt, now, days[which]));
                    } else if (which == days.length) {
                        pickCustomDate();
                    } else {
                        markReviewed(0);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickCustomDate() {
        final Calendar c = Calendar.getInstance();
        c.setTimeInMillis(Ui.sameTimeDaysLater(note.reviewAt, System.currentTimeMillis(), 1));
        DatePickerDialog dlg = new DatePickerDialog(this, (view, y, m, d) -> {
            c.set(Calendar.YEAR, y);
            c.set(Calendar.MONTH, m);
            c.set(Calendar.DAY_OF_MONTH, d);
            new TimePickerDialog(this, (tv, h, min) -> {
                c.set(Calendar.HOUR_OF_DAY, h);
                c.set(Calendar.MINUTE, min);
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);
                if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_MONTH, 1);
                markReviewed(c.getTimeInMillis());
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dlg.getDatePicker().setMinDate(Ui.startOfDay(System.currentTimeMillis()));
        dlg.show();
    }

    private void markReviewed(long nextAt) {
        note.reviewCount++;
        note.reviewAt = nextAt;
        note.notified = false;
        NoteDb.get(this).save(note);
        ReminderScheduler.dismissNotification(this, note.id);
        ReminderScheduler.schedule(this, note);
        bind();
        Toast.makeText(this, nextAt > 0
                ? "Sonraki tekrar: " + Ui.formatFriendly(nextAt)
                : "Tekrar kaydedildi", Toast.LENGTH_SHORT).show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_title)
                .setMessage(R.string.delete_msg)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    ReminderScheduler.cancel(this, note.id);
                    ReminderScheduler.dismissNotification(this, note.id);
                    for (String img : note.images) ImageStore.delete(this, img);
                    NoteDb.get(this).delete(note.id);
                    Toast.makeText(this, "Not silindi", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
