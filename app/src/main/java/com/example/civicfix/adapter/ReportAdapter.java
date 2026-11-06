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
import com.example.civicfix.databinding.ItemReportBinding;
import com.example.civicfix.model.FeedItem;

import java.util.Locale;
import java.util.Objects;

public class ReportAdapter extends ListAdapter<FeedItem, ReportAdapter.VH> {

    private static final DiffUtil.ItemCallback<FeedItem> DIFF =
            new DiffUtil.ItemCallback<FeedItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull FeedItem a, @NonNull FeedItem b) {
                    return a.id.equals(b.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull FeedItem a, @NonNull FeedItem b) {
                    return a.upvotes == b.upvotes
                            && Objects.equals(a.status, b.status)
                            && Objects.equals(a.photoUrl, b.photoUrl)
                            && Objects.equals(a.address, b.address)
                            && Objects.equals(a.description, b.description);
                }
            };

    public ReportAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemReportBinding b = ItemReportBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemReportBinding b;

        VH(ItemReportBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind(FeedItem f) {
            b.tvDepartment.setText(f.category);
            b.tvStatus.setText(statusLabel(f.status));
            b.tvDescription.setText(f.description);

            String where = (f.address != null && !f.address.isEmpty())
                    ? f.address
                    : String.format(Locale.US, "%.4f, %.4f", f.latitude, f.longitude);
            b.tvAddress.setText("📍 " + where);

            b.tvTime.setText(DateUtils.getRelativeTimeSpanString(
                    f.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
            b.tvUpvotes.setText("👍 " + f.upvotes + " noticed this");

            b.tvUrgent.setVisibility(f.isHighUrgency() ? View.VISIBLE : View.GONE);

            Glide.with(b.ivPhoto).load(f.photoUrl).into(b.ivPhoto);
        }

        private static String statusLabel(String status) {
            if ("Under Inspection".equals(status)) return "🔵 Under Inspection";
            if ("Resolved".equals(status)) return "🟢 Resolved";
            return "🟡 Submitted";
        }
    }
}