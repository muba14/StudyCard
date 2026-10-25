package com.studycard.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Ana ekran: Bugün / Yaklaşan / Tümü sekmeleri ve arama. */
public class MainActivity extends Activity {

    private static final int TAB_TODAY = 0, TAB_UPCOMING = 1, TAB_ALL = 2;
    private static final int REQ_NOTIF = 10;

    private NoteAdapter adapter;
    private ListView list;
    private View empty;
    private TextView tabToday, tabUpcoming, tabAll, tvSummary, tvDueCount, tvGreeting,
            tvEmptyTitle, tvEmptyText, bannerExact;
    private EditText etSearch;
    private ImageView btnClearSearch;

    private int tab = -1;
    private List<Note> all = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);
        tabToday = findViewById(R.id.tabToday);
        tabUpcoming = findViewById(R.id.tabUpcoming);
        tabAll = findViewById(R.id.tabAll);
        tvSummary = findViewById(R.id.tvSummary);
        tvDueCount = findViewById(R.id.tvDueCount);
        tvGreeting = findViewById(R.id.tvGreeting);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptyText = findViewById(R.id.tvEmptyText);
        bannerExact = findViewById(R.id.bannerExact);
        etSearch = findViewById(R.id.etSearch);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        View fab = findViewById(R.id.fab);

        final int fabMargin = Ui.dp(this, 22);
        Ui.edgeToEdge(this, findViewById(R.id.header), list, bottom -> {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fab.getLayoutParams();
            lp.bottomMargin = fabMargin + bottom;
            fab.setLayoutParams(lp);
        });

        adapter = new NoteAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) ->
                startActivity(new Intent(this, NoteDetailActivity.class)
                        .putExtra(NoteDetailActivity.EXTRA_ID, id)));

        fab.setOnClickListener(v -> startActivity(new Intent(this, EditNoteActivity.class)));

        tabToday.setOnClickListener(v -> selectTab(TAB_TODAY));
        tabUpcoming.setOnClickListener(v -> selectTab(TAB_UPCOMING));
        tabAll.setOnClickListener(v -> selectTab(TAB_ALL));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                applyFilter();
            }
        });
        btnClearSearch.setOnClickListener(v -> etSearch.setText(""));

        bannerExact.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception ignored) {
                }
            }
        });

        askNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        all = NoteDb.get(this).getAll();
        ReminderScheduler.rescheduleAll(this);
        bannerExact.setVisibility(ReminderScheduler.canScheduleExact(this) ? View.GONE : View.VISIBLE);
        updateHeader();
        if (tab < 0) {
            selectTab(countDue() > 0 ? TAB_TODAY : TAB_ALL);
        } else {
            applyFilter();
        }
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            return;
        SharedPreferences sp = getSharedPreferences("app", MODE_PRIVATE);
        if (sp.getBoolean("asked_notif", false)) return;
        sp.edit().putBoolean("asked_notif", true).apply();
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
    }

    private int countDue() {
        int n = 0;
        for (Note note : all) if (Ui.isDueToday(note.reviewAt)) n++;
        return n;
    }

    private void updateHeader() {
        tvGreeting.setText(Ui.greeting() + " 👋");
        int due = countDue();
        int upcoming = 0;
        long end = Ui.endOfToday();
        for (Note n : all) if (n.reviewAt > end) upcoming++;
        tvDueCount.setText(String.valueOf(due));
        if (all.isEmpty()) {
            tvSummary.setText("Ders notlarını ekle, tekrar gününü seç.");
        } else if (due > 0) {
            tvSummary.setText(due + " notun bugün tekrar edilmeyi bekliyor.");
        } else if (upcoming > 0) {
            tvSummary.setText("Bugün için tekrar yok · " + upcoming + " yaklaşan tekrar var.");
        } else {
            tvSummary.setText(all.size() + " ders notun var.");
        }
        tabToday.setText(due > 0 ? "Bugün  " + due : getString(R.string.tab_today));
        tabUpcoming.setText(upcoming > 0 ? "Yaklaşan  " + upcoming : getString(R.string.tab_upcoming));
        tabAll.setText(all.isEmpty() ? getString(R.string.tab_all) : "Tümü  " + all.size());
    }

    private void selectTab(int t) {
        tab = t;
        styleTab(tabToday, t == TAB_TODAY);
        styleTab(tabUpcoming, t == TAB_UPCOMING);
        styleTab(tabAll, t == TAB_ALL);
        applyFilter();
        list.setSelection(0);
    }

    private void styleTab(TextView v, boolean selected) {
        v.setBackgroundResource(selected ? R.drawable.bg_pill_selected : R.drawable.bg_pill);
        v.setTextColor(selected ? 0xFFFFFFFF : 0xFF1E5EFF);
    }

    private void applyFilter() {
        String q = etSearch.getText().toString().trim().toLowerCase(Ui.TR);
        long end = Ui.endOfToday();
        List<Note> out = new ArrayList<>();
        for (Note n : all) {
            if (tab == TAB_TODAY && !(n.reviewAt > 0 && n.reviewAt <= end)) continue;
            if (tab == TAB_UPCOMING && !(n.reviewAt > end)) continue;
            if (!q.isEmpty()) {
                String hay = (n.title + " " + n.subject + " " + n.content).toLowerCase(Ui.TR);
                if (!hay.contains(q)) continue;
            }
            out.add(n);
        }
        if (tab == TAB_TODAY || tab == TAB_UPCOMING) {
            out.sort((a, b) -> Long.compare(a.reviewAt, b.reviewAt));
        }
        adapter.setItems(out);

        boolean isEmpty = out.isEmpty();
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (isEmpty) {
            if (!q.isEmpty()) {
                tvEmptyTitle.setText("Sonuç bulunamadı");
                tvEmptyText.setText(String.format(Locale.getDefault(), "\"%s\" ile eşleşen not yok.", q));
            } else if (tab == TAB_TODAY && !all.isEmpty()) {
                tvEmptyTitle.setText(R.string.empty_today_title);
                tvEmptyText.setText(R.string.empty_today_text);
            } else if (tab == TAB_UPCOMING && !all.isEmpty()) {
                tvEmptyTitle.setText("Yaklaşan tekrar yok");
                tvEmptyText.setText("Bir notu açıp tekrar tarihi seçtiğinde burada görünecek.");
            } else {
                tvEmptyTitle.setText(R.string.empty_title);
                tvEmptyText.setText(R.string.empty_text);
            }
        }
    }
}
