package com.aware.ui.esms;

import android.content.Context;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;

import com.aware.ESM;
import com.aware.R;

import org.json.JSONArray;
import org.json.JSONException;

/**
 * Created by denzilferreira on 21/02/16.
 */
public class ESM_Checkbox extends ESM_Question implements IESM {

    private String esm_checkboxes = "esm_checkboxes";

    public ESM_Checkbox() throws JSONException {
        this.setType(ESM.TYPE_ESM_CHECKBOX);
    }

    public JSONArray getCheckboxes() throws JSONException {
        if(!this.esm.has(esm_checkboxes)) {
            this.esm.put(esm_checkboxes, new JSONArray());
        }
        return this.esm.getJSONArray(esm_checkboxes);
    }

    public ESM_Question setCheckboxes(JSONArray checkboxes) throws JSONException {
        this.esm.put(this.esm_checkboxes, checkboxes);
        return this;
    }

    public ESM_Question addCheck(String option) throws JSONException {
        JSONArray checks = getCheckboxes();
        checks.put(option);
        this.setCheckboxes(checks);
        return this;
    }

    public ESM_Question removeCheck(String option) throws JSONException {
        JSONArray checks = getCheckboxes();
        JSONArray newChecks = new JSONArray();
        for(int i=0; i<checks.length();i++) {
            if(checks.getString(i).equals(option)) continue;
            newChecks.put(checks.getString(i));
        }
        this.setCheckboxes(newChecks);
        return this;
    }

    @Override
    public void show(FragmentManager fragmentManager, String tag) {
        super.show(fragmentManager, tag);
    }

//    @Override
//    View getView(Context context) throws JSONException {
//        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
//        return inflater.inflate(R.layout.esm_checkbox, null);
//    }
}
