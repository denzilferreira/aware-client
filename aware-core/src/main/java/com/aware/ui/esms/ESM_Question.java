package com.aware.ui.esms;

/**
 * Created by denzilferreira on 21/02/16.
 */

import android.content.Context;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Builder class for ESM questions. Any new ESM type needs to extend this class.
 */
public abstract class ESM_Question {

    public JSONObject esm = new JSONObject();

    private final String esm_type = "esm_type";
    private final String esm_title = "esm_title";
    private final String esm_instructions = "esm_instructions";
    private final String esm_submit = "esm_submit";
    private final String esm_expiration_threshold = "esm_expiration_threshold";
    private final String esm_trigger = "esm_trigger";
    private final String esm_flows = "esm_flows";
    private final String esm_id = "esm_id";

    private final String flow_user_answer = "user_answer";
    private final String flow_next_esm_id = "next_esm_id";

    public int getType() throws JSONException {
        if (!this.esm.has(esm_type)) return -1;
        return this.esm.getInt(esm_type);
    }

    /**
     * Set ESM type ID. AWARE includes:
     * com.aware.ESM#TYPE_ESM_TEXT
     * com.aware.ESM#TYPE_ESM_RADIO
     * com.aware.ESM#TYPE_ESM_CHECKBOX
     * com.aware.ESM#TYPE_ESM_LIKERT
     * com.aware.ESM#TYPE_ESM_QUICK_ANSWERS
     * com.aware.ESM#TYPE_ESM_SCALE
     *
     * @param esm_type
     * @return
     * @throws JSONException
     */
    public ESM_Question setType(int esm_type) throws JSONException {
        this.esm.put(this.esm_type, esm_type);
        return this;
    }

    public String getTitle() throws JSONException {
        if (!this.esm.has(esm_title)) {
            this.esm.put(esm_title, "");
        }
        return this.esm.getString(esm_title);
    }

    /**
     * Set ESM title, limited to about 50 characters due to phone's screen size when in portrait.
     *
     * @param esm_title
     * @return
     * @throws JSONException
     */
    public ESM_Question setTitle(String esm_title) throws JSONException {
        this.esm.put(this.esm_title, esm_title);
        return this;
    }

    public String getInstructions() throws JSONException {
        if (!this.esm.has(esm_instructions)) {
            this.esm.put(esm_instructions, "");
        }
        return this.esm.getString(esm_instructions);
    }

    public ESM_Question setInstructions(String esm_instructions) throws JSONException {
        this.esm.put(this.esm_instructions, esm_instructions);
        return this;
    }

    public String getNextButton() throws JSONException {
        if (!this.esm.has(esm_submit)) {
            this.esm.put(this.esm_submit, "OK");
        }
        return this.esm.getString(this.esm_submit);
    }

    public ESM_Question setNextButton(String esm_submit) throws JSONException {
        this.esm.put(this.esm_submit, esm_submit);
        return this;
    }

    public int getExpirationThreshold() throws JSONException {
        if (!this.esm.has(this.esm_expiration_threshold)) {
            this.esm.put(this.esm_expiration_threshold, 0);
        }
        return this.esm.getInt(this.esm_expiration_threshold);
    }

    /**
     * For how long this question is visible waiting for the users' interaction
     *
     * @param esm_expiration_threshold
     * @return
     * @throws JSONException
     */
    public ESM_Question setExpirationThreshold(int esm_expiration_threshold) throws JSONException {
        this.esm.put(this.esm_expiration_threshold, esm_expiration_threshold);
        return this;
    }

    public String getTrigger() throws JSONException {
        if (!this.esm.has(this.esm_trigger)) {
            this.esm.put(this.esm_trigger, "");
        }
        return this.esm.getString(this.esm_trigger);
    }

    /**
     * A label for what triggered this ESM
     *
     * @param esm_trigger
     * @return
     * @throws JSONException
     */
    public ESM_Question setTrigger(String esm_trigger) throws JSONException {
        this.esm.put(this.esm_trigger, esm_trigger);
        return this;
    }

    /**
     * Get question ID
     *
     * @return
     * @throws JSONException
     */
    public String getID() throws JSONException {
        if (!this.esm.has(this.esm_id)) {
            this.esm.put(this.esm_id, "");
        }
        return this.esm.getString(this.esm_id);
    }

    /**
     * Set question ID
     *
     * @param esm_id
     * @return
     * @throws JSONException
     */
    public ESM_Question setID(String esm_id) throws JSONException {
        this.esm.put(this.esm_id, esm_id);
        return this;
    }

    public JSONObject build() throws JSONException {
        JSONObject esm = new JSONObject();
        esm.put("esm", this.esm);
        return esm;
    }

    /**
     * Rebuild ESM_Question object from database JSON
     *
     * @param esm
     * @return
     * @throws JSONException
     */
    public ESM_Question rebuild(JSONObject esm) throws JSONException {
        this.esm = esm.getJSONObject("esm");
        return this;
    }

    /**
     * Get questionnaire flow.
     *
     * @return
     * @throws JSONException
     */
    public JSONArray getFlows() throws JSONException {
        if (!this.esm.has(this.esm_flows)) {
            this.esm.put(this.esm_flows, new JSONArray());
        }
        return this.esm.getJSONArray(this.esm_flows);
    }

    /**
     * Set questionnaire flow.
     *
     * @param esm_flow
     * @return
     * @throws JSONException
     */
    public ESM_Question setFlows(JSONArray esm_flow) throws JSONException {
        this.esm.put(this.esm_flows, esm_flow);
        return this;
    }

    /**
     * Add a flow condition to this ESM
     * @param user_answer
     * @param nextEsmID
     * @return
     * @throws JSONException
     */
    public ESM_Question addFlow(String user_answer, String nextEsmID) throws JSONException {
        JSONArray flows = getFlows();
        flows.put(new JSONObject()
                .put(flow_user_answer, user_answer)
                .put(flow_next_esm_id, nextEsmID));

        this.setFlows(flows);
        return this;
    }

    /**
     * Given user's answer, what's the next esm ID?
     * @param user_answer
     * @return
     * @throws JSONException
     */
    public String getFlow(String user_answer) throws JSONException {
        JSONArray flows = getFlows();
        for(int i=0;i<flows.length();i++) {
            JSONObject flow = flows.getJSONObject(i);
            if( flow.getString(flow_user_answer).equals(user_answer) )
                return flow.getString(flow_next_esm_id);
        }
        return null;
    }

    /**
     * Remove a flow condition from this ESM based on user's answer
     * @param user_answer
     * @return
     * @throws JSONException
     */
    public ESM_Question removeFlow(String user_answer) throws JSONException {
        JSONArray flows = getFlows();
        JSONArray new_flows = new JSONArray();
        for(int i=0;i<flows.length();i++) {
            JSONObject flow = flows.getJSONObject(i);
            if( flow.getString(flow_user_answer).equals(user_answer) ) continue;
            new_flows.put(flow);
        }
        this.setFlows(new_flows);
        return this;
    }

    /**
     * Return ESM interface
     * @param context
     * @return
     */
    abstract View getView(Context context) throws JSONException;
}
