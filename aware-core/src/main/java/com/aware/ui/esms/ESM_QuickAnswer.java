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
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.aware.Aware;
import com.aware.ESM;
import com.aware.R;
import com.aware.providers.ESM_Provider;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * Created by denzilferreira on 21/02/16.
 */
public class ESM_QuickAnswer extends ESM_Question {

    public static final String esm_quick_answers = "esm_quick_answers";

    public ESM_QuickAnswer() throws JSONException {
        this.setType(ESM.TYPE_ESM_QUICK_ANSWERS);
    }

    public JSONArray getQuickAnswers() throws JSONException {
        if (!this.esm.has(esm_quick_answers)) {
            this.esm.put(esm_quick_answers, new JSONArray());
        }
        return this.esm.getJSONArray(esm_quick_answers);
    }

    public ESM_QuickAnswer setQuickAnswers(JSONArray quickAnswers) throws JSONException {
        this.esm.put(this.esm_quick_answers, quickAnswers);
        return this;
    }

    public ESM_QuickAnswer addQuickAnswer(String answer) throws JSONException {
        JSONArray quicks = getQuickAnswers();
        quicks.put(answer);
        this.setQuickAnswers(quicks);
        return this;
    }

    public ESM_QuickAnswer removeQuickAnswer(String answer) throws JSONException {
        JSONArray quick = getQuickAnswers();
        JSONArray newQuick = new JSONArray();
        for (int i = 0; i < quick.length(); i++) {
            if (quick.getString(i).equals(answer)) continue;
            newQuick.put(quick.getString(i));
        }
        this.setQuickAnswers(newQuick);
        return this;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View ui = inflater.inflate(R.layout.esm_quick, null);
        builder.setView(ui);

        esm_dialog = builder.create();
        esm_dialog.setCanceledOnTouchOutside(false);

        try {
            TextView esm_title = (TextView) ui.findViewById(R.id.esm_title);
            esm_title.setText(getTitle());

            TextView esm_instructions = (TextView) ui.findViewById(R.id.esm_instructions);
            esm_instructions.setText(getInstructions());

            final JSONArray answers = getQuickAnswers();
            final LinearLayout answersHolder = (LinearLayout) ui.findViewById(R.id.esm_answers);

            //If we have more than 3 possibilities, use a vertical layout for UX
            if (answers.length() > 3) {
                answersHolder.setOrientation(LinearLayout.VERTICAL);
            }

            for (int i = 0; i < answers.length(); i++) {
                final Button answer = new Button(getActivity());
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, 1.0f);
                //Fixed: buttons now of the same height regardless of content.
                params.height = WindowManager.LayoutParams.MATCH_PARENT;
                answer.setLayoutParams(params);
                answer.setText(answers.getString(i));
                answer.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            if (getExpirationThreshold() > 0 && expire_monitor != null)
                                expire_monitor.cancel(true);

                            ContentValues rowData = new ContentValues();
                            rowData.put(ESM_Provider.ESM_Data.ANSWER_TIMESTAMP, System.currentTimeMillis());
                            rowData.put(ESM_Provider.ESM_Data.STATUS, ESM.STATUS_ANSWERED);
                            rowData.put(ESM_Provider.ESM_Data.ANSWER, (String) answer.getText());

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
                answersHolder.addView(answer);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return esm_dialog;
    }
}
