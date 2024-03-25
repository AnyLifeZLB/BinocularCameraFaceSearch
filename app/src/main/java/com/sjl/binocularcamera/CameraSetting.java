package com.sjl.binocularcamera;

import android.content.res.Resources;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

/**
 * 旋转摄像头角度
 *
 */
public class CameraSetting extends BaseActivity{
    Spinner spinner_preview_degree;
    @Override
    protected int getLayoutId() {
        return R.layout.activity_camera_setting;
    }

    @Override
    protected void initView() {
        spinner_preview_degree = findViewById(R.id.spinner_preview_degree);
    }

    @Override
    protected void initData() {
        Resources res = getResources();

        final String[] previewDegrees = res.getStringArray(R.array.arr_preview_degree);
        spinner_preview_degree.setAdapter(new ArrayAdapter<String>(this, R.layout.spinner_item, previewDegrees));
        spinner_preview_degree.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, final int position, long id) {
                if (position == 0){
                    Constant.previewDegree = -1;
                    return;
                }
                Constant.previewDegree = Integer.parseInt(previewDegrees[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }
}
