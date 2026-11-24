package io.github.mocklocation.oss.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.github.mocklocation.oss.R;
import io.github.mocklocation.oss.data.PlaceStore;

/** 收藏 / 历史列表适配器：显示名称、坐标、时间，右侧更多按钮弹出操作菜单。 */
public final class PlaceAdapter extends RecyclerView.Adapter<PlaceAdapter.Holder> {

    /** 列表交互回调。 */
    public interface Listener {
        void onPlaceClick(PlaceStore.PlaceRecord record);

        void onPlaceMenu(PlaceStore.PlaceRecord record, View anchor);
    }

    private final Listener listener;
    private final boolean favorites;
    private final List<PlaceStore.PlaceRecord> items = new ArrayList<>();

    public PlaceAdapter(Listener listener, boolean favorites) {
        this.listener = listener;
        this.favorites = favorites;
    }

    public void submit(List<PlaceStore.PlaceRecord> records) {
        items.clear();
        if (records != null) {
            items.addAll(records);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_place, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final PlaceStore.PlaceRecord record = items.get(position);
        holder.name.setText(FormatUtil.placeDisplayName(holder.itemView.getContext(),
                record.name, record.id));
        holder.coordinates.setText(FormatUtil.formatCoordinate(holder.itemView.getContext(), record.point));
        holder.time.setText(FormatUtil.formatTimestamp(holder.itemView.getContext(), record.timestamp));
        holder.icon.setImageResource(favorites ? R.drawable.ic_star : R.drawable.ic_history);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onPlaceClick(record);
            }
        });
        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                listener.onPlaceMenu(record, v);
                return true;
            }
        });
        holder.more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onPlaceMenu(record, v);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView coordinates;
        final TextView time;
        final ImageButton more;

        Holder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.iv_place_icon);
            name = itemView.findViewById(R.id.tv_place_name);
            coordinates = itemView.findViewById(R.id.tv_place_coordinates);
            time = itemView.findViewById(R.id.tv_place_time);
            more = itemView.findViewById(R.id.btn_place_more);
        }
    }
}
