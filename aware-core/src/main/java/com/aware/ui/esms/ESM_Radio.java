package com.aware.ui.esms;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import com.aware.ESM;
import com.aware.R;

import org.json.JSONArray;
import org.json.JSONException;

/**
 * Created by denzilferreira on 21/02/16.
 */
public class ESM_Radio extends ESM_Question {

    private String esm_radios = "esm_radios";

    public ESM_Radio() throws JSONException {
        this.setType(ESM.TYPE_ESM_RADIO);
    }

    public JSONArray getRadios() throws JSONException {
        if(!this.esm.has(esm_radios)) {
            this.esm.put(esm_radios, new JSONArray());
        }
        return this.esm.getJSONArray(esm_radios);
    }

    public ESM_Question setRadios(JSONArray radios) throws JSONException {
        this.esm.put(this.esm_radios, radios);
        return this;
    }

    public ESM_Question addRadio(String option) throws JSONException {
        JSONArray radios = getRadios();
        radios.put(option);
        this.setRadios(radios);
        return this;
    }

    public ESM_Question removeRadio(String option) throws JSONException {
        JSONArray radios = getRadios();
        JSONArray newRadios = new JSONArray();
        for(int i=0; i<radios.length();i++) {
            if(radios.getString(i).equals(option)) continue;
            newRadios.put(radios.getString(i));
        }
        this.setRadios(newRadios);
        return this;
    }

    @Override
    View getView(Context context) throws JSONException {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        return inflater.inflate(R.layout.esm_radio, null);
    }
}
