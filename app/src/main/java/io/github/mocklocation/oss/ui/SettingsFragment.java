package io.github.mocklocation.oss.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

import io.github.mocklocation.oss.R;
import io.github.mocklocation.oss.core.CoordinateSystem;

/**
 * 设置页：地图提供商、默认坐标系、模拟定位自检入口、关于。
 *
 * <p>地图提供商与坐标系写入 SettingsStore；切换地图提供商立即生效（由 MainActivity 通知地图页）。</p>
 */
public final class SettingsFragment extends Fragment {

    private UiHost host;
    private RadioGroup mapProviderGroup;
    private RadioGroup coordinateSystemGroup;

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
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mapProviderGroup = view.findViewById(R.id.rg_map_provider);
        coordinateSystemGroup = view.findViewById(R.id.rg_coordinate_system);
        MaterialButton selfCheckButton = view.findViewById(R.id.btn_self_check);
        MaterialButton aboutButton = view.findViewById(R.id.btn_about);

        checkMapProvider(host.getMapProvider());
        checkCoordinateSystem(host.getCoordinateSystem());

        mapProviderGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                String provider = providerForButton(checkedId);
                host.setMapProvider(provider);
                CharSequence label = labelForProvider(provider);
                host.showToast(getString(R.string.map_provider_switched, label == null ? provider : label.toString()));
            }
        });
        coordinateSystemGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                CoordinateSystem system = systemForButton(checkedId);
                host.setCoordinateSystem(system);
                host.showToast(getString(R.string.coordinate_system_switched, getString(labelRes(system))));
            }
        });
        selfCheckButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openSelfCheck();
            }
        });
        aboutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openAbout();
            }
        });
    }

    private void checkMapProvider(String provider) {
        int buttonId = R.id.rb_provider_auto;
        if ("osm".equals(provider)) {
            buttonId = R.id.rb_provider_osm;
        } else if ("carto".equals(provider)) {
            buttonId = R.id.rb_provider_carto;
        } else if ("opentopo".equals(provider)) {
            buttonId = R.id.rb_provider_opentopo;
        }
        mapProviderGroup.check(buttonId);
    }

    private void checkCoordinateSystem(CoordinateSystem system) {
        int buttonId = R.id.rb_coord_wgs84_setting;
        if (system == CoordinateSystem.GCJ02) {
            buttonId = R.id.rb_coord_gcj02_setting;
        } else if (system == CoordinateSystem.BD09) {
            buttonId = R.id.rb_coord_bd09_setting;
        }
        coordinateSystemGroup.check(buttonId);
    }

    private static String providerForButton(int checkedId) {
        if (checkedId == R.id.rb_provider_osm) {
            return "osm";
        }
        if (checkedId == R.id.rb_provider_carto) {
            return "carto";
        }
        if (checkedId == R.id.rb_provider_opentopo) {
            return "opentopo";
        }
        return "auto";
    }

    private CharSequence labelForProvider(String provider) {
        int resId;
        if ("osm".equals(provider)) {
            resId = R.string.map_provider_osm;
        } else if ("carto".equals(provider)) {
            resId = R.string.map_provider_carto;
        } else if ("opentopo".equals(provider)) {
            resId = R.string.map_provider_opentopo;
        } else {
            resId = R.string.map_provider_auto;
        }
        return getString(resId);
    }

    private static CoordinateSystem systemForButton(int checkedId) {
        if (checkedId == R.id.rb_coord_gcj02_setting) {
            return CoordinateSystem.GCJ02;
        }
        if (checkedId == R.id.rb_coord_bd09_setting) {
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
}
