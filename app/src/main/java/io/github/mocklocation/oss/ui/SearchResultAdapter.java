package io.github.mocklocation.oss.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.github.mocklocation.oss.R;
import io.github.mocklocation.oss.core.GeoPoint;

/** 地址搜索结果列表适配器。 */
public final class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.Holder> {

    /** 结果点选回调。 */
    public interface Listener {
        void onSearchResultClick(SearchResult result);
    }

    private final Listener listener;
    private final List<SearchResult> items = new ArrayList<>();

    public SearchResultAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<SearchResult> results) {
        items.clear();
        if (results != null) {
            items.addAll(results);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_result, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final SearchResult result = items.get(position);
        holder.title.setText(result.displayName);
        holder.subtitle.setText(FormatUtil.formatCoordinate(holder.itemView.getContext(),
                new GeoPoint(result.latitude, result.longitude)));
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onSearchResultClick(result);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;

        Holder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_search_title);
            subtitle = itemView.findViewById(R.id.tv_search_subtitle);
        }
    }
}
