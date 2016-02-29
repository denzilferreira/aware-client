package com.aware.ui.esms;

import com.aware.ESM;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by denzilferreira on 22/02/16.
 */
public class ESMFactory {

    private JSONArray queue;

    public ESMFactory() {
        this.queue = new JSONArray();
    }

    public ESMFactory addESM(ESM_Question esm) {
        try {
            queue.put(esm.build());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this;
    }

    public String build() {
        return this.queue.toString();
    }

    public ESM_Question getESM(int esmType, JSONObject esm) throws JSONException {
        switch (esmType){
            case ESM.TYPE_ESM_TEXT:
                return new ESM_Freetext().setType(esmType).rebuild(esm);
            case ESM.TYPE_ESM_CHECKBOX:
                return new ESM_Checkbox().setType(esmType).rebuild(esm);
            case ESM.TYPE_ESM_LIKERT:
                return new ESM_Likert().setType(esmType).rebuild(esm);
            case ESM.TYPE_ESM_QUICK_ANSWERS:
                return new ESM_QuickAnswer().setType(esmType).rebuild(esm);
            case ESM.TYPE_ESM_RADIO:
                return new ESM_Radio().setType(esmType).rebuild(esm);
            case ESM.TYPE_ESM_SCALE:
                return new ESM_Scale().setType(esmType).rebuild(esm);
            default:
                return null;
        }
    }
}
