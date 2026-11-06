package com.example.civicfix.adapter;

import android.graphics.Color;
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
import java.util.function.Consumer;

public class ReportAdapter extends ListAdapter<FeedItem, ReportAdapter.VH> {

    public interface OnUpvoteClick {
        void onUpvote(FeedItem item);
    }

    private static final DiffUtil.ItemCallback<FeedItem> DIFF =
            new DiffUtil.ItemCallback<FeedItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull FeedItem a, @NonNull FeedItem b) {
                    return a.id.equals(b.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull FeedItem a, @NonNull FeedItem b) {
                    return a.upvotes == b.upvotes
                            && a.voters.equals(b.voters)
                            && Objects.equals(a.status, b.status)
                            && Objects.equals(a.photoUrl, b.photoUrl)
                            && Objects.equals(a.address, b.address)
                            && Objects.equals(a.description, b.description);
                }
            };

    private final OnUpvoteClick upvoteListener;
    private Consumer<FeedItem> openListener;
    private String currentUid;

    public ReportAdapter(OnUpvoteClick upvoteListener) {
        super(DIFF);
        this.upvoteListener = upvoteListener;
    }

    /** Called when a card is tapped (opens the detail screen). */
    public void setOnOpenListener(Consumer<FeedItem> listener) {
        this.openListener = listener;
    }

    /** Called once the anonymous sign-in finishes, so voted buttons light up. */
    public void setCurrentUid(String uid) {
        if (!Objects.equals(uid, currentUid)) {
            currentUid = uid;
            notifyDataSetChanged();
        }
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
        holder.bind(getItem(position), currentUid, upvoteListener, openListener);
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemReportBinding b;

        VH(ItemReportBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind(FeedItem f, String uid, OnUpvoteClick upvote, Consumer<FeedItem> open) {
            b.tvDepartment.setText(f.category);
            b.tvStatus.setText(statusLabel(f.status));
            b.tvDescription.setText(f.description);

            String where = (f.address != null && !f.address.isEmpty())
                    ? f.address
                    : String.format(Locale.US, "%.4f, %.4f", f.latitude, f.longitude);
            b.tvAddress.setText("📍 " + where);

            b.tvTime.setText(DateUtils.getRelativeTimeSpanString(
                    f.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));

            boolean voted = f.hasVoted(uid);
            b.btnUpvote.setText((voted ? "👍 You noticed this" : "👍 I noticed this too")
                    + " · " + f.upvotes);
            b.btnUpvote.setSelected(voted);
            b.btnUpvote.setTextColor(voted ? Color.WHITE : 0xFF0D47A1);
            b.btnUpvote.setOnClickListener(v -> upvote.onUpvote(f));

            b.tvUrgent.setVisibility(f.isHighUrgency() ? View.VISIBLE : View.GONE);

            Glide.with(b.ivPhoto).load(f.photoUrl).into(b.ivPhoto);

            b.getRoot().setOnClickListener(v -> {
                if (open != null) open.accept(f);
            });
        }

        private static String statusLabel(String status) {
            if ("Under Inspection".equals(status)) return "🔵 Under Inspection";
            if ("Resolved".equals(status)) return "🟢 Resolved";
            return "🟡 Submitted";
        }
    }
}