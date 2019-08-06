package com.aware.ui.esms;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RatingBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.aware.Aware;
import com.aware.ESM;
import com.aware.R;
import com.aware.providers.ESM_Provider;
import org.json.JSONException;

/**
 * Created by denzilferreira on 21/02/16.
 */
public class ESM_Likert extends ESM_Question {

    public static final String esm_likert_max = "esm_likert_max";
    public static final String esm_likert_max_label = "esm_likert_max_label";
    public static final String esm_likert_min_label = "esm_likert_min_label";
    public static final String esm_likert_step = "esm_likert_step";

    public ESM_Likert() throws JSONException {
        this.setType(ESM.TYPE_ESM_LIKERT);
    }

    public int getLikertMax() throws JSONException {
        if(!this.esm.has(esm_likert_max)) {
            this.esm.put(esm_likert_max, 5);
        }
        return this.esm.getInt(esm_likert_max);
    }

    public ESM_Likert setLikertMax(int max) throws JSONException {
        this.esm.put(esm_likert_max, max);
        return this;
    }

    public String getLikertMaxLabel() throws JSONException {
        if(!this.esm.has(esm_likert_max_label)) {
            this.esm.put(esm_likert_max_label, "");
        }
        return this.esm.getString(esm_likert_max_label);
    }

    public ESM_Likert setLikertMaxLabel(String label) throws JSONException {
        this.esm.put(esm_likert_max_label, label);
        return this;
    }

    public String getLikertMinLabel() throws JSONException {
        if(!this.esm.has(esm_likert_min_label)) {
            this.esm.put(esm_likert_min_label, "");
        }
        return this.esm.getString(esm_likert_min_label);
    }

    public ESM_Likert setLikertMinLabel(String label) throws JSONException {
        this.esm.put(esm_likert_min_label, label);
        return this;
    }

    public double getLikertStep() throws JSONException {
        if(!this.esm.has(esm_likert_step)) {
            this.esm.put(esm_likert_step, 1.0);
        }
        return this.esm.getDouble(esm_likert_step);
    }

    public ESM_Likert setLikertStep(double step) throws JSONException {
        this.esm.put(esm_likert_step, step);
        return this;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View ui = inflater.inflate(R.layout.esm_likert, null);
        builder.setView(ui);

        esm_dialog = builder.create();
        esm_dialog.setCanceledOnTouchOutside(false);

        try {
            TextView esm_title = (TextView) ui.findViewById(R.id.esm_title);
            esm_title.setText(getTitle());

            TextView esm_instructions = (TextView) ui.findViewById(R.id.esm_instructions);
            esm_instructions.setText(getInstructions());

            final RatingBar ratingBar = (RatingBar) ui.findViewById(R.id.esm_likert);

            ratingBar.setNumStars(getLikertMax());
            ratingBar.setMax(getLikertMax());
            ratingBar.setStepSize((float) getLikertStep());

            ratingBar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        if (getExpirationThreshold() > 0 && expire_monitor != null)
                            expire_monitor.cancel(true);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });

            TextView min_label = (TextView) ui.findViewById(R.id.esm_min);
            min_label.setText(getLikertMinLabel());

            TextView max_label = (TextView) ui.findViewById(R.id.esm_max);
            max_label.setText(getLikertMaxLabel());

            Button cancel = (Button) ui.findViewById(R.id.esm_cancel);
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    esm_dialog.cancel();
                }
            });
            Button submit = (Button) ui.findViewById(R.id.esm_submit);
            submit.setText(getSubmitButton());
            submit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    try{
                        if (getExpirationThreshold() > 0 && expire_monitor != null) expire_monitor.cancel(true);

                        ContentValues rowData = new ContentValues();
                        rowData.put(ESM_Provider.ESM_Data.ANSWER_TIMESTAMP, System.currentTimeMillis());
                        rowData.put(ESM_Provider.ESM_Data.ANSWER, ratingBar.getRating());
                        rowData.put(ESM_Provider.ESM_Data.STATUS, ESM.STATUS_ANSWERED);

                        getContext().getContentResolver().update(ESM_Provider.ESM_Data.CONTENT_URI, rowData, ESM_Provider.ESM_Data._ID + "=" + getID(), null);

                        Intent answer = new Intent(ESM.ACTION_AWARE_ESM_ANSWERED);
                        answer.putExtra(ESM.EXTRA_ANSWER, rowData.getAsString(ESM_Provider.ESM_Data.ANSWER));
                        getActivity().sendBroadcast(answer);

                        if (Aware.DEBUG) Log.d(Aware.TAG, "Answer:" + rowData.toString());

                        esm_dialog.dismiss();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        }catch (JSONException e) {
            e.printStackTrace();
        }

        return esm_dialog;
    }
}
