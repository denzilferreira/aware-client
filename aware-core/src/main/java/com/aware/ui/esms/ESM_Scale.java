package com.aware.ui.esms;

import android.content.Context;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;

import com.aware.ESM;
import com.aware.R;

import org.json.JSONException;

/**
 * Created by denzilferreira on 21/02/16.
 */
public class ESM_Scale extends ESM_Question implements IESM {

    private String esm_scale_min = "esm_scale_min";
    private String esm_scale_min_label = "esm_scale_min_label";
    private String esm_scale_max = "esm_scale_max";
    private String esm_scale_max_label = "esm_scale_max_label";
    private String esm_scale_step = "esm_scale_step";
    private String esm_scale_start = "esm_scale_start";

    public ESM_Scale() throws JSONException {
        this.setType(ESM.TYPE_ESM_SCALE);
    }

    public int getScaleStart() throws JSONException {
        if(!this.esm.has(esm_scale_start)) {
            this.esm.put(esm_scale_start, 0);
        }
        return this.esm.getInt(esm_scale_start);
    }

    public ESM_Question setScaleStart(int start) throws JSONException {
        this.esm.put(esm_scale_start, start);
        return this;
    }

    public int getScaleStep() throws JSONException {
        if(!this.esm.has(esm_scale_step)) {
            this.esm.put(esm_scale_step, 1);
        }
        return this.esm.getInt(esm_scale_step);
    }

    public ESM_Question setScaleStep(int step) throws JSONException {
        this.esm.put(esm_scale_step, step);
        return this;
    }

    public int getScaleMin() throws JSONException {
        if(!this.esm.has(esm_scale_min)) {
            this.esm.put(esm_scale_min, 0);
        }
        return this.esm.getInt(esm_scale_min);
    }

    public ESM_Question setScaleMin(int min) throws JSONException {
        this.esm.put(esm_scale_min, min);
        return this;
    }

    public String getScaleMinLabel() throws JSONException {
        if(!this.esm.has(esm_scale_min_label)) {
            this.esm.put(esm_scale_min_label, "");
        }
        return this.esm.getString(esm_scale_min_label);
    }

    public ESM_Question setScaleMinLabel(String label) throws JSONException {
        this.esm.put(esm_scale_min_label, label);
        return this;
    }

    public int getScaleMax() throws JSONException {
        if(!this.esm.has(esm_scale_max)) {
            this.esm.put(esm_scale_max, 10);
        }
        return this.esm.getInt(esm_scale_max);
    }

    public ESM_Question setScaleMax(int max) throws JSONException {
        this.esm.put(esm_scale_max, max);
        return this;
    }

    public String getScaleMaxLabel() throws JSONException {
        if(!this.esm.has(esm_scale_max_label)) {
            this.esm.put(esm_scale_max_label, "");
        }
        return this.esm.getString(esm_scale_max_label);
    }

    public ESM_Question setScaleMaxLabel(String label) throws JSONException {
        this.esm.put(esm_scale_max_label, label);
        return this;
    }

    @Override
    public void show(FragmentManager fragmentManager, String tag) {
        super.show(fragmentManager, tag);
    }

//    @Override
//    View getView(Context context) throws JSONException {
//        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
//        return inflater.inflate(R.layout.esm_scale, null);
//    }
}
