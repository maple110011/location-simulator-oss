package io.github.mocklocation.oss.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.github.mocklocation.oss.R;
import io.github.mocklocation.oss.data.PlaceStore;

/** 已保存路线列表适配器。 */
public final class RouteAdapter extends RecyclerView.Adapter<RouteAdapter.Holder> {

    /** 列表交互回调。 */
    public interface Listener {
        void onRouteClick(PlaceStore.SavedRoute route);

        void onRouteMenu(PlaceStore.SavedRoute route, View anchor);
    }

    private final Listener listener;
    private final List<PlaceStore.SavedRoute> items = new ArrayList<>();

    public RouteAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<PlaceStore.SavedRoute> routes) {
        items.clear();
        if (routes != null) {
            items.addAll(routes);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_route, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final PlaceStore.SavedRoute route = items.get(position);
        holder.name.setText(FormatUtil.routeDisplayName(holder.itemView.getContext(),
                route.name, route.id));
        boolean loop = route.loop;
        holder.details.setText(holder.itemView.getContext().getString(R.string.route_details,
                route.points.size(),
                route.speedKmh,
                holder.itemView.getContext().getString(loop ? R.string.route_loop_yes : R.string.route_loop_no)));
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onRouteClick(route);
            }
        });
        holder.more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onRouteMenu(route, v);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView details;
        final ImageButton more;

        Holder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_route_name);
            details = itemView.findViewById(R.id.tv_route_details);
            more = itemView.findViewById(R.id.btn_route_more);
        }
    }
}
