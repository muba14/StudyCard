package com.studycard.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Ana ekrandaki not kartları. */
public class NoteAdapter extends BaseAdapter {

    private final Context ctx;
    private final List<Note> items = new ArrayList<>();
    private final int thumbPx;

    public NoteAdapter(Context ctx) {
        this.ctx = ctx;
        this.thumbPx = Ui.dp(ctx, 68);
    }

    public void setItems(List<Note> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Note getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    private static class Holder {
        TextView subject, badge, title, preview, meta;
        ImageView thumb;
        View strip;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder h;
        if (convertView == null) {
            convertView = LayoutInflater.from(ctx).inflate(R.layout.item_note, parent, false);
            h = new Holder();
            h.subject = convertView.findViewById(R.id.tvSubject);
            h.badge = convertView.findViewById(R.id.tvBadge);
            h.title = convertView.findViewById(R.id.tvTitle);
            h.preview = convertView.findViewById(R.id.tvPreview);
            h.meta = convertView.findViewById(R.id.tvMeta);
            h.thumb = convertView.findViewById(R.id.ivThumb);
            h.strip = convertView.findViewById(R.id.strip);
            h.thumb.setClipToOutline(true);
            convertView.setTag(h);
        } else {
            h = (Holder) convertView.getTag();
        }

        Note n = items.get(position);
        h.subject.setText(n.subject == null || n.subject.isEmpty() ? "Genel" : n.subject);
        h.title.setText(n.title);

        String preview = n.content == null ? "" : n.content.trim().replace('\n', ' ');
        h.preview.setText(preview);
        h.preview.setVisibility(preview.isEmpty() ? View.GONE : View.VISIBLE);

        // Rozet: tekrar zamanı
        if (n.hasReminder()) {
            h.badge.setVisibility(View.VISIBLE);
            h.badge.setText(Ui.badge(n.reviewAt));
            boolean due = Ui.isDueToday(n.reviewAt);
            boolean overdue = Ui.isOverdue(n.reviewAt);
            int fg = due ? 0xFFFFFFFF : 0xFF1E5EFF;
            h.badge.setBackgroundResource(overdue ? R.drawable.bg_tag_deep
                    : due ? R.drawable.bg_tag_solid : R.drawable.bg_tag);
            h.badge.setTextColor(fg);
            h.badge.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(fg));
            h.strip.setVisibility(due ? View.VISIBLE : View.INVISIBLE);
        } else {
            h.badge.setVisibility(View.GONE);
            h.strip.setVisibility(View.INVISIBLE);
        }

        // Alt bilgi satırı
        StringBuilder meta = new StringBuilder();
        if (!n.images.isEmpty()) meta.append("▣ ").append(n.images.size()).append(" görsel");
        if (n.hasChart()) {
            if (meta.length() > 0) meta.append("   ");
            meta.append("▤ grafik");
        }
        if (n.reviewCount > 0) {
            if (meta.length() > 0) meta.append("   ");
            meta.append("↻ ").append(n.reviewCount).append(" kez tekrar");
        }
        h.meta.setText(meta);
        h.meta.setVisibility(meta.length() == 0 ? View.GONE : View.VISIBLE);

        if (!n.images.isEmpty()) {
            h.thumb.setVisibility(View.VISIBLE);
            ThumbLoader.load(ctx, n.images.get(0), thumbPx * 2, h.thumb);
        } else {
            h.thumb.setVisibility(View.GONE);
            h.thumb.setTag(R.id.tag_image_key, null);
        }
        return convertView;
    }
}
