package com.aware.ui.esms;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import androidx.annotation.NonNull;
import com.aware.Aware;
import com.aware.ESM;
import com.aware.R;
import com.aware.providers.ESM_Provider;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;

/**
 * Created by denzilferreira on 21/02/16.
 */
public class ESM_Checkbox extends ESM_Question {

    public static final String esm_checkboxes = "esm_checkboxes";

    public ESM_Checkbox() throws JSONException {
        this.setType(ESM.TYPE_ESM_CHECKBOX);
    }

    public JSONArray getCheckboxes() throws JSONException {
        if (!this.esm.has(esm_checkboxes)) {
            this.esm.put(esm_checkboxes, new JSONArray());
        }
        return this.esm.getJSONArray(esm_checkboxes);
    }

    public ESM_Checkbox setCheckboxes(JSONArray checkboxes) throws JSONException {
        this.esm.put(esm_checkboxes, checkboxes);
        return this;
    }

    public ESM_Checkbox addCheck(String option) throws JSONException {
        JSONArray checks = getCheckboxes();
        checks.put(option);
        this.setCheckboxes(checks);
        return this;
    }

    public ESM_Checkbox removeCheck(String option) throws JSONException {
        JSONArray checks = getCheckboxes();
        JSONArray newChecks = new JSONArray();
        for (int i = 0; i < checks.length(); i++) {
            if (checks.getString(i).equals(option)) continue;
            newChecks.put(checks.getString(i));
        }
        this.setCheckboxes(newChecks);
        return this;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        final ArrayList<String> selected_options = new ArrayList<>();

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View ui = inflater.inflate(R.layout.esm_checkbox, null);
        builder.setView(ui);

        esm_dialog = builder.create();
        esm_dialog.setCanceledOnTouchOutside(false);

        try {
            TextView esm_title = (TextView) ui.findViewById(R.id.esm_title);
            esm_title.setText(getTitle());

            TextView esm_instructions = (TextView) ui.findViewById(R.id.esm_instructions);
            esm_instructions.setText(getInstructions());

            final LinearLayout checkboxes = (LinearLayout) ui.findViewById(R.id.esm_checkboxes);
            checkboxes.setOnClickListener(new View.OnClickListener() {
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

            final JSONArray checks = getCheckboxes();
            for (int i = 0; i < checks.length(); i++) {
                final CheckBox checked = new CheckBox(getActivity());

                final int current_position = i;
                checked.setText(checks.getString(i));
                checked.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(final CompoundButton buttonView, boolean isChecked) {
                        try {
                            if (isChecked) {
                                if (checks.getString(current_position).equals(getResources().getString(R.string.aware_esm_other))) {
                                    checked.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            final Dialog editOther = new Dialog(getActivity());
                                            editOther.setTitle(getResources().getString(R.string.aware_esm_other_follow));
                                            editOther.getWindow().setGravity(Gravity.TOP);
                                            editOther.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);

                                            LinearLayout editor = new LinearLayout(getActivity());
                                            editor.setOrientation(LinearLayout.VERTICAL);
                                            editOther.setContentView(editor);
                                            editOther.show();

                                            final EditText otherText = new EditText(getActivity());
                                            otherText.setHint(getResources().getString(R.string.aware_esm_other_follow));
                                            editor.addView(otherText);
                                            otherText.requestFocus();
                                            editOther.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

                                            Button confirm = new Button(getActivity());
                                            confirm.setText("OK");
                                            confirm.setOnClickListener(new View.OnClickListener() {
                                                @Override
                                                public void onClick(View v) {
                                                    if (otherText.length() > 0) {
                                                        selected_options.remove(buttonView.getText().toString());
                                                        checked.setText(otherText.getText());
                                                        selected_options.add(otherText.getText().toString());
                                                    }
                                                    editOther.dismiss();
                                                }
                                            });
                                            editor.addView(confirm);
                                        }
                                    });
                                } else {
                                    selected_options.add(buttonView.getText().toString());
                                }
                            } else {
                                selected_options.remove(buttonView.getText().toString());
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
                checkboxes.addView(checked);
            }
            Button cancel_checkbox = (Button) ui.findViewById(R.id.esm_cancel);
            cancel_checkbox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    esm_dialog.cancel();
                }
            });
            Button submit_checkbox = (Button) ui.findViewById(R.id.esm_submit);
            submit_checkbox.setText(getSubmitButton());
            submit_checkbox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        if (getExpirationThreshold() > 0 && expire_monitor != null)
                            expire_monitor.cancel(true);

                        ContentValues rowData = new ContentValues();
                        rowData.put(ESM_Provider.ESM_Data.ANSWER_TIMESTAMP, System.currentTimeMillis());
                        if (selected_options.size() > 0) {
                            rowData.put(ESM_Provider.ESM_Data.ANSWER,
                                    selected_options.toString()
                                            .replace("[", "").replace("]", "")
                            );
                        }
                        rowData.put(ESM_Provider.ESM_Data.STATUS, ESM.STATUS_ANSWERED);

                        getContext().getContentResolver().update(ESM_Provider.ESM_Data.CONTENT_URI, rowData, ESM_Provider.ESM_Data._ID + "=" + getID(), null);
                        selected_options.clear();

                        Intent answer = new Intent(ESM.ACTION_AWARE_ESM_ANSWERED);
                        answer.putExtra(ESM.EXTRA_ANSWER, rowData.getAsString(ESM_Provider.ESM_Data.ANSWER));
                        getActivity().sendBroadcast(answer);

                        if (Aware.DEBUG) Log.d(Aware.TAG, "Answer: " + rowData.toString());
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
