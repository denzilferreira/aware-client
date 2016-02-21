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
public class ESM_Freetext extends ESM_Question {

    public ESM_Freetext() throws JSONException {
        this.setType(ESM.TYPE_ESM_TEXT);
    }

    @Override
    View getView(Context context) throws JSONException {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        return inflater.inflate(R.layout.esm_text, null);
    }
}
