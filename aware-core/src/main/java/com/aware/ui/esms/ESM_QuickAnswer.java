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
public class ESM_QuickAnswer extends ESM_Question implements IESM {

    private String esm_quick_answers = "esm_quick_answers";

    public ESM_QuickAnswer() throws JSONException {
        this.setType(ESM.TYPE_ESM_QUICK_ANSWERS);
    }

    public JSONArray getQuickAnswers() throws JSONException {
        if(!this.esm.has(esm_quick_answers)) {
            this.esm.put(esm_quick_answers, new JSONArray());
        }
        return this.esm.getJSONArray(esm_quick_answers);
    }

    public ESM_Question setQuickAnswers(JSONArray quickAnswers) throws JSONException {
        this.esm.put(this.esm_quick_answers, quickAnswers);
        return this;
    }

    public ESM_Question addQuickAnswer(String answer) throws JSONException {
        JSONArray quicks = getQuickAnswers();
        quicks.put(answer);
        this.setQuickAnswers(quicks);
        return this;
    }

    public ESM_Question removeQuickAnswer(String answer) throws JSONException {
        JSONArray quick = getQuickAnswers();
        JSONArray newQuick = new JSONArray();
        for(int i=0; i<quick.length(); i++) {
            if(quick.getString(i).equals(answer)) continue;
            newQuick.put(quick.getString(i));
        }
        this.setQuickAnswers(newQuick);
        return this;
    }

    @Override
    public void show(FragmentManager fragmentManager, String tag) {
        super.show(fragmentManager, tag);
    }

//    @Override
//    View getView(Context context) throws JSONException {
//        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
//        return inflater.inflate(R.layout.esm_quick, null);
//    }
}
