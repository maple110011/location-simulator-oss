package io.github.mocklocation.oss.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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
 * 收藏页 / 历史页共用的列表页。
 *
 * <p>点击条目 = 选中该点并回到地图；右侧“更多”或长按 = 重命名 / 删除（收藏页为“取消收藏”）。
 * 数据来自 PlaceStore（收藏 type=FAVORITE，历史 type=HISTORY）。</p>
 */
public final class PlaceListFragment extends Fragment implements Refreshable {

    private static final String ARG_FAVORITES = "arg_favorites";
    private static final int MENU_RENAME = 1;
    private static final int MENU_DELETE = 2;

    private UiHost host;
    private boolean favorites;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private PlaceAdapter adapter;

    public static PlaceListFragment newInstance(boolean favorites) {
        PlaceListFragment fragment = new PlaceListFragment();
        Bundle arguments = new Bundle();
        arguments.putBoolean(ARG_FAVORITES, favorites);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (!(context instanceof UiHost)) {
            throw new IllegalStateException("host activity must implement UiHost");
        }
        host = (UiHost) context;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle arguments = getArguments();
        favorites = arguments != null && arguments.getBoolean(ARG_FAVORITES, false);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_place_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.recycler_places);
        emptyView = view.findViewById(R.id.tv_empty_places);
        emptyView.setText(favorites ? R.string.no_favorites : R.string.no_history);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new PlaceAdapter(new PlaceAdapter.Listener() {
            @Override
            public void onPlaceClick(PlaceStore.PlaceRecord record) {
                host.onPlaceSelected(record);
            }

            @Override
            public void onPlaceMenu(PlaceStore.PlaceRecord record, View anchor) {
                showMenu(record, anchor);
            }
        }, favorites);
        recyclerView.setAdapter(adapter);
        reload();
    }

    @Override
    public void refreshData() {
        reload();
    }

    private void reload() {
        final PlaceStore.RecordType type = favorites
                ? PlaceStore.RecordType.FAVORITE : PlaceStore.RecordType.HISTORY;
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<PlaceStore.PlaceRecord> records = null;
                try {
                    records = host.getPlaceStore().listPlaces(type);
                } catch (Throwable ignored) {
                    records = null;
                }
                final List<PlaceStore.PlaceRecord> result = records == null
                        ? new ArrayList<PlaceStore.PlaceRecord>() : records;
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
        }, "place-list-load").start();
    }

    private void showMenu(final PlaceStore.PlaceRecord record, View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, MENU_RENAME, 0, R.string.action_rename);
        popup.getMenu().add(0, MENU_DELETE, 1,
                favorites ? R.string.action_remove_favorite : R.string.action_delete);
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                if (item.getItemId() == MENU_RENAME) {
                    showRenameDialog(record);
                    return true;
                }
                if (item.getItemId() == MENU_DELETE) {
                    showDeleteDialog(record);
                    return true;
                }
                return false;
            }
        });
        popup.show();
    }

    private void showRenameDialog(final PlaceStore.PlaceRecord record) {
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_input_text, null);
        final EditText input = content.findViewById(R.id.et_dialog_input);
        input.setHint(R.string.location_name_hint);
        if (record.name != null) {
            input.setText(record.name);
            input.setSelection(input.getText().length());
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.rename_place_title)
                .setView(content)
                .setPositiveButton(R.string.action_ok, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        final String name = input.getText().toString().trim();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                boolean saved = true;
                                try {
                                    host.getPlaceStore().updatePlaceName(record.id, name);
                                } catch (Throwable t) {
                                    saved = false;
                                }
                                final boolean result = saved;
                                host.runOnMainThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        host.showToast(result ? R.string.place_renamed : R.string.place_save_failed);
                                        if (result) {
                                            reload();
                                        }
                                    }
                                });
                            }
                        }, "place-rename").start();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showDeleteDialog(final PlaceStore.PlaceRecord record) {
        String displayName = FormatUtil.placeDisplayName(requireContext(), record.name, record.id);
        new AlertDialog.Builder(requireContext())
                .setTitle(favorites ? R.string.action_remove_favorite : R.string.delete_place_title)
                .setMessage(getString(R.string.delete_place_message, displayName))
                .setPositiveButton(favorites ? R.string.action_remove_favorite : R.string.action_delete,
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                deleteRecord(record);
                            }
                        })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void deleteRecord(final PlaceStore.PlaceRecord record) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean deleted = true;
                try {
                    host.getPlaceStore().deletePlace(record.id);
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
                        host.showToast(favorites ? R.string.favorite_removed : R.string.place_deleted);
                        reload();
                    }
                });
            }
        }, "place-delete").start();
    }
}
