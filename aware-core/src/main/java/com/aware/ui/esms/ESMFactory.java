package com.aware.ui.esms;

import com.aware.ESM;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by denzilferreira on 22/02/16.
 */
public class ESMFactory {
    public ESM_Question getESM(int esmType, JSONObject esm, int _id) throws JSONException {
        switch (esmType){
            case ESM.TYPE_ESM_TEXT:
                return new ESM_Freetext().setType(esmType).rebuild(esm).setID(_id);
//            case ESM.TYPE_ESM_CHECKBOX:
//                return new ESM_Checkbox(esm);
//            case ESM.TYPE_ESM_LIKERT:
//                return new ESM_Likert(esm);
//            case ESM.TYPE_ESM_QUICK_ANSWERS:
//                return new ESM_QuickAnswer(esm);
//            case ESM.TYPE_ESM_RADIO:
//                return new ESM_Radio(esm);
//            case ESM.TYPE_ESM_SCALE:
//                return new ESM_Scale(esm);
            default:
                return null;
        }
    }
}
