package com.aware.ui.esms;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import com.aware.ESM;
import com.aware.R;

import org.json.JSONException;

/**
 * Created by denzilferreira on 21/02/16.
 */
public class ESM_Likert extends ESM_Question {

    private String esm_likert_max = "esm_likert_max";
    private String esm_likert_max_label = "esm_likert_max_label";
    private String esm_likert_min_label = "esm_likert_min_label";
    private String esm_likert_step = "esm_likert_step";

    public ESM_Likert() throws JSONException {
        this.setType(ESM.TYPE_ESM_LIKERT);
    }

    public int getLikertMax() throws JSONException {
        if(!this.esm.has(esm_likert_max)) {
            this.esm.put(esm_likert_max, 5);
        }
        return this.esm.getInt(esm_likert_max);
    }

    public ESM_Question setLikertMax(int max) throws JSONException {
        this.esm.put(esm_likert_max, max);
        return this;
    }

    public String getLikertMaxLabel() throws JSONException {
        if(!this.esm.has(esm_likert_max_label)) {
            this.esm.put(esm_likert_max_label, "");
        }
        return this.esm.getString(esm_likert_max_label);
    }

    public ESM_Question setLikertMaxLabel(String label) throws JSONException {
        this.esm.put(esm_likert_max_label, label);
        return this;
    }

    public String getLikertMinLabel() throws JSONException {
        if(!this.esm.has(esm_likert_min_label)) {
            this.esm.put(esm_likert_min_label, "");
        }
        return this.esm.getString(esm_likert_min_label);
    }

    public ESM_Question setLikertMinLabel(String label) throws JSONException {
        this.esm.put(esm_likert_min_label, label);
        return this;
    }

    public double getLikertStep() throws JSONException {
        if(!this.esm.has(esm_likert_step)) {
            this.esm.put(esm_likert_step, 1.0);
        }
        return this.esm.getDouble(esm_likert_step);
    }

    public ESM_Question setLikertStep(double step) throws JSONException {
        this.esm.put(esm_likert_step, step);
        return this;
    }

    @Override
    View getView(Context context) throws JSONException {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        return inflater.inflate(R.layout.esm_likert, null);
    }
}
