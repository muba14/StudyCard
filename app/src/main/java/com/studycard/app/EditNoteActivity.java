package com.studycard.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Not ekleme / düzenleme ekranı: yazı, görsel, grafik ve tekrar tarihi. */
public class EditNoteActivity extends Activity {

    public static final String EXTRA_ID = "id";
    private static final int REQ_GALLERY = 1, REQ_CAMERA = 2;
    private static final int[] QUICK_DAYS = {1, 3, 7, 14, 30};
    private static final String[] QUICK_LABELS = {"Yarın", "3 gün", "1 hafta", "2 hafta", "1 ay"};

    private long noteId;
    private Note note;

    private AutoCompleteTextView etSubject;
    private EditText etTitle, etContent, etChartTitle;
    private LinearLayout imagesContainer, rowsContainer, chartEditor, quickContainer;
    private View imagesScroll, btnAddChart, btnClearReview;
    private TextView tvNoImages, tvReview, typeBar, typeLine, typePie;
    private ChartView chartPreview;

    private ArrayList<String> originalImages = new ArrayList<>();
    private ArrayList<String> currentImages = new ArrayList<>();
    private ArrayList<String> addedImages = new ArrayList<>();
    private long reviewAt = 0;
    private boolean chartVisible = false;
    private String chartType = Note.CHART_BAR;
    private String pendingCamera;
    private String initialSnapshot;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_edit);
        bindViews();
        Ui.edgeToEdge(this, findViewById(R.id.header), findViewById(R.id.content), null);

        noteId = getIntent().getLongExtra(EXTRA_ID, 0);
        note = noteId != 0 ? NoteDb.get(this).get(noteId) : null;
        if (note == null) {
            note = new Note();
            noteId = 0;
        }
        ((TextView) findViewById(R.id.tvHeader)).setText(noteId == 0 ? R.string.new_note : R.string.edit_note);

        etSubject.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, NoteDb.get(this).getSubjects()));

        List<Note.ChartEntry> rows;
        if (saved != null) {
            originalImages = listOrEmpty(saved.getStringArrayList("orig"));
            currentImages = listOrEmpty(saved.getStringArrayList("cur"));
            addedImages = listOrEmpty(saved.getStringArrayList("added"));
            reviewAt = saved.getLong("reviewAt");
            chartVisible = saved.getBoolean("chartVisible");
            chartType = saved.getString("chartType", Note.CHART_BAR);
            pendingCamera = saved.getString("pendingCamera");
            initialSnapshot = saved.getString("snapshot");
            rows = Note.parseChart(saved.getString("chart", ""));
        } else {
            etSubject.setText(note.subject);
            etTitle.setText(note.title);
            etContent.setText(note.content);
            etChartTitle.setText(note.chartTitle);
            originalImages = new ArrayList<>(note.images);
            currentImages = new ArrayList<>(note.images);
            reviewAt = note.reviewAt;
            chartVisible = note.hasChart();
            chartType = note.chartType;
            rows = note.chartEntries;
            String preset = getIntent().getStringExtra("subject");
            if (noteId == 0 && preset != null) etSubject.setText(preset);
        }

        for (String img : currentImages) addImageView(img);
        updateImagesUi();

        for (Note.ChartEntry e : rows) addRow(e.label, e.value);
        if (chartVisible && rowsContainer.getChildCount() == 0) {
            addRow("", Float.NaN);
            addRow("", Float.NaN);
        }
        setChartVisible(chartVisible);
        selectType(chartType);

        buildQuickPills();
        updateReviewUi();

        if (initialSnapshot == null) initialSnapshot = snapshot();
        wireListeners();
    }

    private static ArrayList<String> listOrEmpty(ArrayList<String> l) {
        return l == null ? new ArrayList<>() : l;
    }

    private void bindViews() {
        etSubject = findViewById(R.id.etSubject);
        etTitle = findViewById(R.id.etTitle);
        etContent = findViewById(R.id.etContent);
        etChartTitle = findViewById(R.id.etChartTitle);
        imagesContainer = findViewById(R.id.imagesContainer);
        imagesScroll = findViewById(R.id.imagesScroll);
        tvNoImages = findViewById(R.id.tvNoImages);
        rowsContainer = findViewById(R.id.rowsContainer);
        chartEditor = findViewById(R.id.chartEditor);
        btnAddChart = findViewById(R.id.btnAddChart);
        chartPreview = findViewById(R.id.chartPreview);
        typeBar = findViewById(R.id.typeBar);
        typeLine = findViewById(R.id.typeLine);
        typePie = findViewById(R.id.typePie);
        quickContainer = findViewById(R.id.quickContainer);
        tvReview = findViewById(R.id.tvReview);
        btnClearReview = findViewById(R.id.btnClearReview);
    }

    private void wireListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        findViewById(R.id.btnGallery).setOnClickListener(v -> openGallery());
        findViewById(R.id.btnCamera).setOnClickListener(v -> openCamera());

        btnAddChart.setOnClickListener(v -> {
            if (rowsContainer.getChildCount() == 0) {
                addRow("", Float.NaN);
                addRow("", Float.NaN);
                addRow("", Float.NaN);
            }
            setChartVisible(true);
        });
        findViewById(R.id.btnRemoveChart).setOnClickListener(v -> {
            rowsContainer.removeAllViews();
            etChartTitle.setText("");
            setChartVisible(false);
        });
        findViewById(R.id.btnAddRow).setOnClickListener(v -> {
            EditText et = addRow("", Float.NaN);
            et.requestFocus();
        });
        typeBar.setOnClickListener(v -> selectType(Note.CHART_BAR));
        typeLine.setOnClickListener(v -> selectType(Note.CHART_LINE));
        typePie.setOnClickListener(v -> selectType(Note.CHART_PIE));

        findViewById(R.id.btnPickDate).setOnClickListener(v -> pickDate());
        findViewById(R.id.btnPickTime).setOnClickListener(v -> pickTime(false));
        btnClearReview.setOnClickListener(v -> {
            reviewAt = 0;
            updateReviewUi();
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putStringArrayList("orig", originalImages);
        out.putStringArrayList("cur", currentImages);
        out.putStringArrayList("added", addedImages);
        out.putLong("reviewAt", reviewAt);
        out.putBoolean("chartVisible", chartVisible);
        out.putString("chartType", chartType);
        out.putString("pendingCamera", pendingCamera);
        out.putString("snapshot", initialSnapshot);
        out.putString("chart", Note.serializeChart(collectRows(true)));
    }

    // ------------------------------------------------------------------
    // Görseller
    // ------------------------------------------------------------------

    private void openGallery() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(i, REQ_GALLERY);
        } catch (ActivityNotFoundException e) {
            Intent g = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*");
            g.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            try {
                startActivityForResult(g, REQ_GALLERY);
            } catch (ActivityNotFoundException e2) {
                toast("Galeri açılamadı");
            }
        }
    }

    private void openCamera() {
        pendingCamera = ImageStore.newName();
        Uri uri = ImageProvider.uriFor(this, pendingCamera);
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        i.setClipData(ClipData.newRawUri("photo", uri));
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_CAMERA);
        } catch (ActivityNotFoundException | SecurityException e) {
            pendingCamera = null;
            toast("Kamera uygulaması bulunamadı");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAMERA) {
            final String name = pendingCamera;
            pendingCamera = null;
            if (name == null) return;
            if (resultCode != RESULT_OK) {
                ImageStore.delete(this, name);
                return;
            }
            new Thread(() -> {
                boolean ok = ImageStore.processCameraFile(this, name);
                runOnUiThread(() -> {
                    if (ok) addImage(name);
                    else {
                        ImageStore.delete(this, name);
                        toast("Fotoğraf kaydedilemedi");
                    }
                });
            }).start();
        } else if (requestCode == REQ_GALLERY && resultCode == RESULT_OK && data != null) {
            final List<Uri> uris = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (uris.isEmpty()) return;
            toast(uris.size() > 1 ? uris.size() + " görsel ekleniyor…" : "Görsel ekleniyor…");
            new Thread(() -> {
                List<String> names = new ArrayList<>();
                for (Uri u : uris) {
                    String n = ImageStore.importUri(this, u);
                    if (n != null) names.add(n);
                }
                runOnUiThread(() -> {
                    for (String n : names) addImage(n);
                    if (names.size() < uris.size()) toast("Bazı görseller eklenemedi");
                });
            }).start();
        }
    }

    private void addImage(String name) {
        if (isFinishing()) {
            ImageStore.delete(this, name);
            return;
        }
        currentImages.add(name);
        addedImages.add(name);
        addImageView(name);
        updateImagesUi();
    }

    private void addImageView(String name) {
        int size = Ui.dp(this, 96);
        FrameLayout frame = new FrameLayout(this);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(size, size);
        flp.setMarginEnd(Ui.dp(this, 10));
        frame.setLayoutParams(flp);
        frame.setTag(name);

        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundResource(R.drawable.bg_thumb);
        iv.setClipToOutline(true);
        frame.addView(iv, new FrameLayout.LayoutParams(size, size));
        ThumbLoader.load(this, name, size * 2, iv);
        iv.setOnClickListener(v -> ImageViewerDialog.show(this, name));

        ImageView remove = new ImageView(this);
        int rs = Ui.dp(this, 26);
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(rs, rs, Gravity.TOP | Gravity.END);
        rlp.setMargins(0, Ui.dp(this, 5), Ui.dp(this, 5), 0);
        remove.setLayoutParams(rlp);
        remove.setBackgroundResource(R.drawable.bg_remove_badge);
        remove.setImageResource(R.drawable.ic_close);
        remove.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        int p = Ui.dp(this, 5);
        remove.setPadding(p, p, p, p);
        remove.setContentDescription("Görseli kaldır");
        remove.setOnClickListener(v -> {
            currentImages.remove(name);
            if (addedImages.remove(name)) ImageStore.delete(this, name);
            imagesContainer.removeView(frame);
            updateImagesUi();
        });
        frame.addView(remove);

        imagesContainer.addView(frame);
    }

    private void updateImagesUi() {
        boolean has = !currentImages.isEmpty();
        imagesScroll.setVisibility(has ? View.VISIBLE : View.GONE);
        tvNoImages.setVisibility(has ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------------
    // Grafik
    // ------------------------------------------------------------------

    private void setChartVisible(boolean v) {
        chartVisible = v;
        chartEditor.setVisibility(v ? View.VISIBLE : View.GONE);
        btnAddChart.setVisibility(v ? View.GONE : View.VISIBLE);
        updatePreview();
    }

    private void selectType(String t) {
        chartType = t == null ? Note.CHART_BAR : t;
        stylePill(typeBar, Note.CHART_BAR.equals(chartType));
        stylePill(typeLine, Note.CHART_LINE.equals(chartType));
        stylePill(typePie, Note.CHART_PIE.equals(chartType));
        updatePreview();
    }

    private void stylePill(TextView v, boolean sel) {
        v.setBackgroundResource(sel ? R.drawable.bg_pill_selected : R.drawable.bg_pill);
        v.setTextColor(sel ? 0xFFFFFFFF : 0xFF1E5EFF);
    }

    private final TextWatcher previewWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {
        }

        @Override
        public void onTextChanged(CharSequence s, int a, int b, int c) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            updatePreview();
        }
    };

    /** Bir grafik satırı ekler; etiket alanını döndürür. */
    private EditText addRow(String label, float value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = Ui.dp(this, 8);
        row.setLayoutParams(rlp);

        EditText etLabel = makeField(getString(R.string.hint_label),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        etLabel.setText(label == null ? "" : label);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f);
        l1.setMarginEnd(Ui.dp(this, 8));
        row.addView(etLabel, l1);

        EditText etValue = makeField(getString(R.string.hint_value),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        if (!Float.isNaN(value)) {
            etValue.setText(value == (long) value ? String.valueOf((long) value) : String.valueOf(value));
        }
        row.addView(etValue, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ImageView remove = new ImageView(this);
        int s = Ui.dp(this, 40);
        remove.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        remove.setImageResource(R.drawable.ic_close);
        remove.setImageTintList(android.content.res.ColorStateList.valueOf(0xFF94A3C0));
        int p = Ui.dp(this, 10);
        remove.setPadding(p, p, p, p);
        remove.setBackgroundResource(R.drawable.bg_ripple_blue_round);
        remove.setContentDescription("Satırı sil");
        remove.setOnClickListener(v -> {
            rowsContainer.removeView(row);
            updatePreview();
        });
        row.addView(remove);

        etLabel.addTextChangedListener(previewWatcher);
        etValue.addTextChangedListener(previewWatcher);
        rowsContainer.addView(row);
        updatePreview();
        return etLabel;
    }

    private EditText makeField(String hint, int inputType) {
        EditText et = new EditText(this);
        et.setBackgroundResource(R.drawable.bg_input);
        et.setHint(hint);
        et.setInputType(inputType);
        et.setTextSize(15);
        et.setTextColor(0xFF0F1B3D);
        et.setHintTextColor(0xFF94A3C0);
        et.setSingleLine(true);
        int h = Ui.dp(this, 12), v = Ui.dp(this, 10);
        et.setPadding(h, v, h, v);
        return et;
    }

    /** Satırlardan grafik verisini toplar. keepPartial: kaydetme durumu için eksikleri de tut. */
    private List<Note.ChartEntry> collectRows(boolean keepPartial) {
        List<Note.ChartEntry> out = new ArrayList<>();
        for (int i = 0; i < rowsContainer.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) rowsContainer.getChildAt(i);
            String label = ((EditText) row.getChildAt(0)).getText().toString().trim();
            String raw = ((EditText) row.getChildAt(1)).getText().toString().trim().replace(',', '.');
            if (label.isEmpty() && raw.isEmpty()) continue;
            float val;
            try {
                val = Float.parseFloat(raw);
            } catch (NumberFormatException e) {
                if (!keepPartial) continue;
                val = Float.NaN;
            }
            out.add(new Note.ChartEntry(label.isEmpty() ? String.valueOf(i + 1) : label, val));
        }
        return out;
    }

    private void updatePreview() {
        if (chartPreview == null || rowsContainer == null) return;
        chartPreview.setChart(chartType, collectRows(false));
    }

    // ------------------------------------------------------------------
    // Tekrar tarihi
    // ------------------------------------------------------------------

    private void buildQuickPills() {
        quickContainer.removeAllViews();
        for (int i = 0; i < QUICK_DAYS.length; i++) {
            final int days = QUICK_DAYS[i];
            TextView pill = new TextView(this);
            pill.setText(QUICK_LABELS[i]);
            pill.setTextSize(14);
            pill.setTextColor(0xFF1E5EFF);
            pill.setGravity(Gravity.CENTER);
            pill.setBackgroundResource(R.drawable.bg_pill);
            pill.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            int ph = Ui.dp(this, 16);
            pill.setPadding(ph, 0, ph, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 36));
            lp.setMarginEnd(Ui.dp(this, 8));
            pill.setLayoutParams(lp);
            pill.setOnClickListener(v -> {
                reviewAt = Ui.sameTimeDaysLater(reviewAt, System.currentTimeMillis(), days);
                updateReviewUi();
            });
            quickContainer.addView(pill);
        }
    }

    private void updateReviewUi() {
        if (reviewAt > 0) {
            tvReview.setText(Ui.formatFull(reviewAt));
            btnClearReview.setVisibility(View.VISIBLE);
        } else {
            tvReview.setText(R.string.no_review);
            btnClearReview.setVisibility(View.GONE);
        }
    }

    private void pickDate() {
        final Calendar c = Calendar.getInstance();
        if (reviewAt > 0) c.setTimeInMillis(reviewAt);
        else {
            c.add(Calendar.DAY_OF_MONTH, 1);
            c.set(Calendar.HOUR_OF_DAY, 9);
            c.set(Calendar.MINUTE, 0);
        }
        DatePickerDialog dlg = new DatePickerDialog(this, (view, y, m, d) -> {
            c.set(Calendar.YEAR, y);
            c.set(Calendar.MONTH, m);
            c.set(Calendar.DAY_OF_MONTH, d);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            reviewAt = c.getTimeInMillis();
            updateReviewUi();
            pickTime(true);
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dlg.getDatePicker().setMinDate(Ui.startOfDay(System.currentTimeMillis()));
        dlg.show();
    }

    private void pickTime(boolean afterDate) {
        final Calendar c = Calendar.getInstance();
        if (reviewAt > 0) c.setTimeInMillis(reviewAt);
        else {
            c.set(Calendar.HOUR_OF_DAY, 9);
            c.set(Calendar.MINUTE, 0);
        }
        new TimePickerDialog(this, (view, h, min) -> {
            Calendar r = Calendar.getInstance();
            if (reviewAt > 0) r.setTimeInMillis(reviewAt);
            r.set(Calendar.HOUR_OF_DAY, h);
            r.set(Calendar.MINUTE, min);
            r.set(Calendar.SECOND, 0);
            r.set(Calendar.MILLISECOND, 0);
            if (r.getTimeInMillis() <= System.currentTimeMillis()) {
                if (reviewAt > 0 && afterDate) {
                    toast("Geçmiş bir saat seçtin; yarına ayarlandı");
                }
                r.add(Calendar.DAY_OF_MONTH, 1);
            }
            reviewAt = r.getTimeInMillis();
            updateReviewUi();
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
    }

    // ------------------------------------------------------------------
    // Kaydet / çık
    // ------------------------------------------------------------------

    private String snapshot() {
        return etSubject.getText() + "\u0001" + etTitle.getText() + "\u0001" + etContent.getText()
                + "\u0001" + currentImages + "\u0001" + (chartVisible ? chartType + etChartTitle.getText()
                + Note.serializeChart(collectRows(true)) : "") + "\u0001" + reviewAt;
    }

    private void save() {
        String title = etTitle.getText().toString().trim();
        String content = etContent.getText().toString();
        List<Note.ChartEntry> chart = chartVisible ? collectRows(false) : new ArrayList<>();

        if (title.isEmpty()) {
            String firstLine = content.trim().split("\n", 2)[0].trim();
            if (!firstLine.isEmpty()) {
                title = firstLine.length() > 50 ? firstLine.substring(0, 50) + "…" : firstLine;
            } else if (!currentImages.isEmpty() || !chart.isEmpty()) {
                title = "Adsız not";
            } else {
                etTitle.setError("Bir başlık ya da not yaz");
                etTitle.requestFocus();
                return;
            }
        }

        boolean reviewChanged = reviewAt != note.reviewAt;
        note.subject = etSubject.getText().toString().trim();
        note.title = title;
        note.content = content.trim();
        note.images = new ArrayList<>(currentImages);
        note.chartType = chartType;
        note.chartTitle = chartVisible ? etChartTitle.getText().toString().trim() : "";
        note.chartEntries = chart;
        note.reviewAt = reviewAt;
        if (reviewChanged) note.notified = false;

        NoteDb.get(this).save(note);

        // Kaldırılan eski görselleri dosyadan da sil
        for (String img : originalImages) {
            if (!currentImages.contains(img)) ImageStore.delete(this, img);
        }
        addedImages.clear();
        ThumbLoader.evict(null);

        if (reviewChanged) {
            ReminderScheduler.dismissNotification(this, note.id);
            ReminderScheduler.schedule(this, note);
        }

        if (note.reviewAt > 0) {
            toast("Kaydedildi · Tekrar: " + Ui.formatFriendly(note.reviewAt));
        } else {
            toast("Kaydedildi");
        }
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_ID, note.id));
        finish();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (snapshot().equals(initialSnapshot)) {
            discardAndFinish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.discard_title)
                .setMessage(R.string.discard_msg)
                .setPositiveButton(R.string.discard, (d, w) -> discardAndFinish())
                .setNegativeButton(R.string.keep_editing, null)
                .show();
    }

    private void discardAndFinish() {
        for (String img : addedImages) ImageStore.delete(this, img);
        addedImages.clear();
        finish();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
