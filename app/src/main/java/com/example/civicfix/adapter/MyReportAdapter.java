package com.example.civicfix.adapter;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.civicfix.databinding.ItemMyReportBinding;
import com.example.civicfix.model.Report;

import java.io.File;
import java.util.Objects;

public class MyReportAdapter extends ListAdapter<Report, MyReportAdapter.VH> {

    public interface Listener {
        void onOpen(Report report);

        void onDelete(Report report);
    }

    private static final DiffUtil.ItemCallback<Report> DIFF =
            new DiffUtil.ItemCallback<Report>() {
                @Override
                public boolean areItemsTheSame(@NonNull Report a, @NonNull Report b) {
                    return a.id == b.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull Report a, @NonNull Report b) {
                    return Objects.equals(a.syncState, b.syncState)
                            && Objects.equals(a.cloudId, b.cloudId)
                            && Objects.equals(a.description, b.description)
                            && Objects.equals(a.photoUrl, b.photoUrl);
                }
            };

    private final Listener listener;

    public MyReportAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMyReportBinding b = ItemMyReportBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemMyReportBinding b;

        VH(ItemMyReportBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind(Report r, Listener listener) {
            b.tvDescription.setText(r.description);
            b.tvMeta.setText(r.category + " · " + r.severity + " · "
                    + DateUtils.getRelativeTimeSpanString(r.createdAt,
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));

            boolean pending = Report.SYNC_PENDING.equals(r.syncState);
            b.tvStatus.setText(pending ? "⏳ Waiting to upload" : "✅ Uploaded · tap to view");
            b.tvStatus.setTextColor(pending ? 0xFFE65100 : 0xFF2E7D32);

            b.btnDelete.setVisibility(pending ? View.VISIBLE : View.GONE);
            b.btnDelete.setOnClickListener(v -> listener.onDelete(r));
            b.getRoot().setOnClickListener(v -> listener.onOpen(r));

            // Show the photo stored on the phone, or the uploaded copy if the file is gone
            File local = r.localPhotoPath != null ? new File(r.localPhotoPath) : null;
            Object source = (local != null && local.exists()) ? local : r.photoUrl;
            Glide.with(b.ivThumb).load(source).into(b.ivThumb);
        }
    }
}