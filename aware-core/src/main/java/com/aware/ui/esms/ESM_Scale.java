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
import android.widget.SeekBar;
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
public class ESM_Scale extends ESM_Question {

    private static int selected_scale_progress = -1;

    public static final String esm_scale_min = "esm_scale_min";
    public static final String esm_scale_min_label = "esm_scale_min_label";
    public static final String esm_scale_max = "esm_scale_max";
    public static final String esm_scale_max_label = "esm_scale_max_label";
    public static final String esm_scale_step = "esm_scale_step";
    public static final String esm_scale_start = "esm_scale_start";

    public ESM_Scale() throws JSONException {
        this.setType(ESM.TYPE_ESM_SCALE);
    }

    public int getScaleStart() throws JSONException {
        if (!this.esm.has(esm_scale_start)) {
            this.esm.put(esm_scale_start, 0);
        }
        return this.esm.getInt(esm_scale_start);
    }

    public ESM_Scale setScaleStart(int start) throws JSONException {
        this.esm.put(esm_scale_start, start);
        return this;
    }

    public int getScaleStep() throws JSONException {
        if (!this.esm.has(esm_scale_step)) {
            this.esm.put(esm_scale_step, 1);
        }
        return this.esm.getInt(esm_scale_step);
    }

    public ESM_Scale setScaleStep(int step) throws JSONException {
        this.esm.put(esm_scale_step, step);
        return this;
    }

    public int getScaleMin() throws JSONException {
        if (!this.esm.has(esm_scale_min)) {
            this.esm.put(esm_scale_min, 0);
        }
        return this.esm.getInt(esm_scale_min);
    }

    public ESM_Scale setScaleMin(int min) throws JSONException {
        this.esm.put(esm_scale_min, min);
        return this;
    }

    public String getScaleMinLabel() throws JSONException {
        if (!this.esm.has(esm_scale_min_label)) {
            this.esm.put(esm_scale_min_label, "");
        }
        return this.esm.getString(esm_scale_min_label);
    }

    public ESM_Scale setScaleMinLabel(String label) throws JSONException {
        this.esm.put(esm_scale_min_label, label);
        return this;
    }

    public int getScaleMax() throws JSONException {
        if (!this.esm.has(esm_scale_max)) {
            this.esm.put(esm_scale_max, 10);
        }
        return this.esm.getInt(esm_scale_max);
    }

    public ESM_Scale setScaleMax(int max) throws JSONException {
        this.esm.put(esm_scale_max, max);
        return this;
    }

    public String getScaleMaxLabel() throws JSONException {
        if (!this.esm.has(esm_scale_max_label)) {
            this.esm.put(esm_scale_max_label, "");
        }
        return this.esm.getString(esm_scale_max_label);
    }

    public ESM_Scale setScaleMaxLabel(String label) throws JSONException {
        this.esm.put(esm_scale_max_label, label);
        return this;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View ui = inflater.inflate(R.layout.esm_scale, null);
        builder.setView(ui);

        esm_dialog = builder.create();
        esm_dialog.setCanceledOnTouchOutside(false);

        try {
            TextView esm_title = (TextView) ui.findViewById(R.id.esm_title);
            esm_title.setText(getTitle());

            TextView esm_instructions = (TextView) ui.findViewById(R.id.esm_instructions);
            esm_instructions.setText(getInstructions());

            final int min_value = getScaleMin();
            final int max_value = getScaleMax();

            selected_scale_progress = getScaleStart();

            final int step_size = getScaleStep();

            final TextView current_slider_value = (TextView) ui.findViewById(R.id.esm_slider_value);
            current_slider_value.setText(String.valueOf(selected_scale_progress));

            final SeekBar seekBar = (SeekBar) ui.findViewById(R.id.esm_scale);
            seekBar.setOnClickListener(new View.OnClickListener() {
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

            seekBar.incrementProgressBy(step_size);

            if (min_value >= 0) {
                seekBar.setProgress(selected_scale_progress);
                seekBar.setMax(max_value);
            } else {
                seekBar.setMax(max_value * 2);
                seekBar.setProgress(max_value); //move handle to center value
            }
            current_slider_value.setText("" + selected_scale_progress);

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        if (min_value < 0) {
                            progress -= max_value;
                        }

                        progress /= step_size;
                        progress *= step_size;

                        selected_scale_progress = progress;

                        if (selected_scale_progress < min_value) {
                            selected_scale_progress = min_value;
                        } else if (selected_scale_progress > max_value) {
                            selected_scale_progress = max_value;
                        }

                        current_slider_value.setText(String.valueOf(selected_scale_progress));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    current_slider_value.setText("" + selected_scale_progress);
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    current_slider_value.setText("" + selected_scale_progress);
                }
            });

            TextView min_scale_label = (TextView) ui.findViewById(R.id.esm_min);
            min_scale_label.setText(getScaleMinLabel());

            TextView max_scale_label = (TextView) ui.findViewById(R.id.esm_max);
            max_scale_label.setText(getScaleMaxLabel());

            Button scale_cancel = (Button) ui.findViewById(R.id.esm_cancel);
            scale_cancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    esm_dialog.cancel();
                }
            });
            Button scale_submit = (Button) ui.findViewById(R.id.esm_submit);
            scale_submit.setText(getSubmitButton());
            scale_submit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    try {
                        if (getExpirationThreshold() > 0 && expire_monitor != null) expire_monitor.cancel(true);

                        ContentValues rowData = new ContentValues();
                        rowData.put(ESM_Provider.ESM_Data.ANSWER_TIMESTAMP, System.currentTimeMillis());
                        rowData.put(ESM_Provider.ESM_Data.ANSWER, selected_scale_progress);
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
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return esm_dialog;
    }
}
