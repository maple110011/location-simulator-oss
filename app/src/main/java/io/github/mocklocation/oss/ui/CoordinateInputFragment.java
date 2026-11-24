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
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import io.github.mocklocation.oss.R;
import io.github.mocklocation.oss.core.CoordinateConverter;
import io.github.mocklocation.oss.core.CoordinateSystem;
import io.github.mocklocation.oss.core.GeoPoint;
import io.github.mocklocation.oss.data.PlaceStore;

/**
 * 坐标输入页：纬度/经度 + 坐标系（WGS84 / GCJ-02 / BD-09）+ 设为当前位置 / 加入收藏。
 *
 * <p>注入系统的坐标统一是 WGS84，因此这里按所选坐标系输入后先换算成 WGS84 再交给 MainActivity。</p>
 */
public final class CoordinateInputFragment extends Fragment {

    private UiHost host;
    private EditText latitudeInput;
    private EditText longitudeInput;
    private MaterialButtonToggleGroup coordinateSystemToggle;
    private EditText nameInput;
    private TextView currentLocation;

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
        return inflater.inflate(R.layout.fragment_coordinate_input, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        latitudeInput = view.findViewById(R.id.et_latitude);
        longitudeInput = view.findViewById(R.id.et_longitude);
        coordinateSystemToggle = view.findViewById(R.id.coord_system_toggle);
        nameInput = view.findViewById(R.id.et_location_name);
        currentLocation = view.findViewById(R.id.tv_current_location);
        MaterialButton setLocationButton = view.findViewById(R.id.btn_set_location);
        MaterialButton addFavoriteButton = view.findViewById(R.id.btn_add_favorite);

        checkSystem(host.getCoordinateSystem());
        coordinateSystemToggle.addOnButtonCheckedListener(new MaterialButtonToggleGroup.OnButtonCheckedListener() {
            @Override
            public void onButtonChecked(MaterialButtonToggleGroup group, int checkedId, boolean isChecked) {
                if (!isChecked) {
                    return;
                }
                CoordinateSystem system = systemForButton(checkedId);
                host.setCoordinateSystem(system);
                host.showToast(getString(R.string.coordinate_system_switched, getString(labelRes(system))));
            }
        });

        setLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyCoordinateInput();
            }
        });
        addFavoriteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addToFavorites();
            }
        });

        GeoPoint selected = host.getSelectedPoint();
        if (selected != null) {
            showCurrentLocation(selected);
            if (host.getSelectedLabel() != null) {
                nameInput.setText(host.getSelectedLabel());
            }
        }
    }

    private void checkSystem(CoordinateSystem system) {
        int buttonId = R.id.btn_coord_wgs84;
        if (system == CoordinateSystem.GCJ02) {
            buttonId = R.id.btn_coord_gcj02;
        } else if (system == CoordinateSystem.BD09) {
            buttonId = R.id.btn_coord_bd09;
        }
        coordinateSystemToggle.check(buttonId);
    }

    private CoordinateSystem systemForButton(int checkedId) {
        if (checkedId == R.id.btn_coord_gcj02) {
            return CoordinateSystem.GCJ02;
        }
        if (checkedId == R.id.btn_coord_bd09) {
            return CoordinateSystem.BD09;
        }
        return CoordinateSystem.WGS84;
    }

    private static int labelRes(CoordinateSystem system) {
        switch (system) {
            case GCJ02:
                return R.string.coord_gcj02;
            case BD09:
                return R.string.coord_bd09;
            case WGS84:
            default:
                return R.string.coord_wgs84;
        }
    }

    private void applyCoordinateInput() {
        String latitudeText = latitudeInput.getText().toString().trim();
        String longitudeText = longitudeInput.getText().toString().trim();
        if (latitudeText.isEmpty() || longitudeText.isEmpty()) {
            host.showToast(R.string.error_coordinate_empty);
            return;
        }
        double latitude;
        double longitude;
        try {
            latitude = Double.parseDouble(latitudeText);
            longitude = Double.parseDouble(longitudeText);
        } catch (NumberFormatException e) {
            host.showToast(R.string.error_invalid_number);
            return;
        }
        if (latitude < -90.0 || latitude > 90.0) {
            host.showToast(R.string.error_lat_range);
            return;
        }
        if (longitude < -180.0 || longitude > 180.0) {
            host.showToast(R.string.error_lng_range);
            return;
        }
        if (host.getMockState() != null && host.getMockState().running) {
            host.showToast(R.string.error_stop_before_select);
            return;
        }
        CoordinateSystem from = systemForButton(coordinateSystemToggle.getCheckedButtonId());
        GeoPoint input = new GeoPoint(latitude, longitude);
        GeoPoint wgs84 = CoordinateConverter.convert(input, from, CoordinateSystem.WGS84);
        if (wgs84 == null || !wgs84.isValid()) {
            host.showToast(R.string.error_invalid_number);
            return;
        }
        String name = nameInput.getText().toString().trim();
        host.setSelectedPoint(wgs84, name);
        showCurrentLocation(wgs84);
        host.showToast(R.string.location_set);
    }

    private void addToFavorites() {
        final GeoPoint point = host.getSelectedPoint();
        if (point == null) {
            host.showToast(R.string.error_no_selected_point);
            return;
        }
        final String name = nameInput.getText().toString().trim();
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean added = false;
                try {
                    PlaceStore store = host.getPlaceStore();
                    if (store.findPlace(point, PlaceStore.RecordType.FAVORITE) == null) {
                        store.insertPlace(name, point, PlaceStore.RecordType.FAVORITE);
                        added = true;
                    }
                } catch (Throwable ignored) {
                    added = false;
                }
                final boolean result = added;
                host.runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        host.showToast(result ? R.string.favorite_added : R.string.favorite_already_exists);
                        if (result) {
                            nameInput.setText("");
                        }
                    }
                });
            }
        }, "favorite-insert").start();
    }

    private void showCurrentLocation(GeoPoint point) {
        currentLocation.setText(FormatUtil.formatCoordinate(requireContext(), point));
    }
}
