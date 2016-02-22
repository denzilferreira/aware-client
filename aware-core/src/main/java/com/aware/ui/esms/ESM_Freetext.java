package com.aware.ui.esms;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.aware.Aware;
import com.aware.ESM;
import com.aware.R;
import com.aware.providers.ESM_Provider;

import org.json.JSONException;

/**
 * Created by denzilferreira on 21/02/16.
 */
public class ESM_Freetext extends ESM_Question {

    public ESM_Freetext() throws JSONException {
        this.setType(ESM.TYPE_ESM_TEXT);
    }

    @Override
    Dialog getDialog(final Activity activity) throws JSONException {
        LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        View ui = inflater.inflate(R.layout.esm_text, null);
        builder.setView(ui);

        final Dialog current_dialog = builder.setTitle(getTitle()).create();

        TextView esm_instructions = (TextView) ui.findViewById(R.id.esm_instructions);
        esm_instructions.setText(getInstructions());

        final EditText feedback = (EditText) ui.findViewById(R.id.esm_feedback);
        feedback.requestFocus();
        current_dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        Button cancel_text = (Button) ui.findViewById(R.id.esm_cancel);
        cancel_text.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                current_dialog.cancel();
            }
        });
        Button submit_text = (Button) ui.findViewById(R.id.esm_submit);
        submit_text.setText(getNextButton());
        submit_text.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ContentValues rowData = new ContentValues();
                rowData.put(ESM_Provider.ESM_Data.ANSWER_TIMESTAMP, System.currentTimeMillis());
                rowData.put(ESM_Provider.ESM_Data.ANSWER, feedback.getText().toString());
                rowData.put(ESM_Provider.ESM_Data.STATUS, ESM.STATUS_ANSWERED);

                activity.getContentResolver().update(ESM_Provider.ESM_Data.CONTENT_URI, rowData, ESM_Provider.ESM_Data._ID + "=" + esm_id, null);

                Intent answer = new Intent(ESM.ACTION_AWARE_ESM_ANSWERED);
                activity.sendBroadcast(answer);

                if (Aware.DEBUG) Log.d(Aware.TAG, "Answer:" + rowData.toString());

                current_dialog.dismiss();
            }
        });
        return current_dialog;
    }
}
