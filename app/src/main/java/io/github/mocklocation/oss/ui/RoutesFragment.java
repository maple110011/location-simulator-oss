package io.github.mocklocation.oss.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.github.mocklocation.oss.R;
import io.github.mocklocation.oss.data.PlaceStore;

/**
 * 路线页：已保存路线列表（名称、点数、速度、是否循环）。
 *
 * <p>点击条目把路线载入地图页的路线模式；右侧“更多”或长按可删除。</p>
 */
public final class RoutesFragment extends Fragment implements Refreshable {

    private UiHost host;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private RouteAdapter adapter;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (!(context instanceof UiHost)) {
            throw new IllegalStateException("host activity must implement UiHost");
        }
        host = (UiHost) context;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_route_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.recycler_routes);
        emptyView = view.findViewById(R.id.tv_empty_routes);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RouteAdapter(new RouteAdapter.Listener() {
            @Override
            public void onRouteClick(PlaceStore.SavedRoute route) {
                host.onRouteSelected(route);
            }

            @Override
            public void onRouteMenu(PlaceStore.SavedRoute route, View anchor) {
                showMenu(route, anchor);
            }
        });
        recyclerView.setAdapter(adapter);
        reload();
    }

    @Override
    public void refreshData() {
        reload();
    }

    private void reload() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<PlaceStore.SavedRoute> routes = null;
                try {
                    routes = host.getPlaceStore().listRoutes();
                } catch (Throwable ignored) {
                    routes = null;
                }
                final List<PlaceStore.SavedRoute> result = routes == null
                        ? new ArrayList<PlaceStore.SavedRoute>() : routes;
                host.runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isAdded() || adapter == null) {
                            return;
                        }
                        adapter.submit(result);
                        emptyView.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
            }
        }, "route-list-load").start();
    }

    private void showMenu(final PlaceStore.SavedRoute route, View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 1, 0, R.string.action_delete);
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                if (item.getItemId() == 1) {
                    showDeleteDialog(route);
                    return true;
                }
                return false;
            }
        });
        popup.show();
    }

    private void showDeleteDialog(final PlaceStore.SavedRoute route) {
        String displayName = FormatUtil.routeDisplayName(requireContext(), route.name, route.id);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_route_title)
                .setMessage(getString(R.string.delete_route_message, displayName))
                .setPositiveButton(R.string.action_delete, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        deleteRoute(route);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void deleteRoute(final PlaceStore.SavedRoute route) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean deleted = true;
                try {
                    host.getPlaceStore().deleteRoute(route.id);
                } catch (Throwable t) {
                    deleted = false;
                }
                final boolean result = deleted;
                host.runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!result) {
                            host.showToast(R.string.place_save_failed);
                            return;
                        }
                        host.showToast(R.string.route_deleted);
                        reload();
                    }
                });
            }
        }, "route-delete").start();
    }
}
