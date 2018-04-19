package com.aware.ui.esms;

import com.aware.ESM;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by denzilferreira on 22/02/16.
 */
public class ESMFactory {

    private JSONArray queue = new JSONArray();

    public ESMFactory() {}

    public JSONArray getQueue() {
        return queue;
    }

    public ESMFactory addESM(ESM_Question esm) {
        try {
            queue.put(esm.build());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this;
    }

    public ESMFactory removeESM(int position) {
        queue.remove(position);
        return this;
    }

    public String build() {
        return queue.toString();
    }

    public ESMFactory rebuild(JSONArray queue) throws JSONException {
        this.queue = queue;
        return this;
    }

    public ESM_Question getESM(int esmType, JSONObject esm, int _id) throws JSONException {
        switch (esmType) {
            case ESM.TYPE_ESM_TEXT:
                return new ESM_Freetext().rebuild(esm).setID(_id);
            case ESM.TYPE_ESM_CHECKBOX:
                return new ESM_Checkbox().rebuild(esm).setID(_id);
            case ESM.TYPE_ESM_LIKERT:
                return new ESM_Likert().rebuild(esm).setID(_id);
            case ESM.TYPE_ESM_QUICK_ANSWERS:
                return new ESM_QuickAnswer().rebuild(esm).setID(_id);
            case ESM.TYPE_ESM_RADIO:
                return new ESM_Radio().rebuild(esm).setID(_id);
            case ESM.TYPE_ESM_SCALE:
                return new ESM_Scale().rebuild(esm).setID(_id);
            case ESM.TYPE_ESM_DATETIME:
                return new ESM_DateTime().rebuild(esm).setID(_id);
            case ESM.TYPE_ESM_PAM:
                return new ESM_PAM().rebuild(esm).setID(_id);
            case ESM.TYPE_ESM_NUMBER:
                return new ESM_Number().rebuild(esm).setID(_id);
            case ESM.TYPE_ESM_WEB:
                return new ESM_Web().rebuild(esm).setID(_id);
            case ESM.TYPE_ESM_DATE:
                return new ESM_Date().rebuild(esm).setID(_id);
            default:
                return null;
        }
    }
}
