package com.aware.ui.esms;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.aware.Aware;
import com.aware.ESM;
import com.aware.R;
import com.aware.providers.ESM_Provider;
import com.koushikdutta.ion.Ion;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by denzil on 02/11/2016.
 * Based on JP at Cornell's work on Photographic Affect Meter (PAM):
 * https://github.com/ohmage/ohmage-pam
 */
public class ESM_PAM extends ESM_Question {

    public static final String esm_pam = "esm_pam";

    private String pam_selected = "";

    public ESM_PAM() throws JSONException {
        this.setType(ESM.TYPE_ESM_PAM);
    }

    /**
     * Get PAM JSONArray with picture URLs
     *
     * @return
     * @throws JSONException
     */
    public JSONArray getPAM() throws JSONException {
        if (!this.esm.has(esm_pam)) {
            this.esm.put(esm_pam, new JSONArray());
        }
        return this.esm.getJSONArray(esm_pam);
    }

    /**
     * Set PAM list of picture URLs
     *
     * @param pam
     * @return
     * @throws JSONException
     */
    public ESM_PAM setPAM(JSONArray pam) throws JSONException {
        this.esm.put(esm_pam, pam);
        return this;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        pam_selected = "";

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View ui = inflater.inflate(R.layout.esm_pam, null);
        builder.setView(ui);

        esm_dialog = builder.create();
        esm_dialog.setCanceledOnTouchOutside(false);

        try {

            esm_dialog.setTitle(getTitle());

            TextView esm_instructions = (TextView) ui.findViewById(R.id.esm_instructions);
            esm_instructions.setText(getInstructions());

            final GridLayout answersHolder = (GridLayout) ui.findViewById(R.id.esm_pam);
            answersHolder.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    try {
                        if (getExpirationThreshold() > 0 && expire_monitor != null)
                            expire_monitor.cancel(true);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });

            //Populate affect grid with photos random positions
            List<Integer> generated = new ArrayList<>();
            JSONArray moods = getPAM(); //0-indexed
            if (moods.length() == 0) {
                //Load by default ours
                moods.put("http://awareframework.com/public/pam/afraid.jpg");
                moods.put("http://awareframework.com/public/pam/angry.jpg");
                moods.put("http://awareframework.com/public/pam/calm.jpg");
                moods.put("http://awareframework.com/public/pam/delighted.jpg");
                moods.put("http://awareframework.com/public/pam/excited.jpg");
                moods.put("http://awareframework.com/public/pam/frustrated.jpg");
                moods.put("http://awareframework.com/public/pam/glad.jpg");
                moods.put("http://awareframework.com/public/pam/gloomy.jpg");
                moods.put("http://awareframework.com/public/pam/happy.jpg");
                moods.put("http://awareframework.com/public/pam/miserable.jpg");
                moods.put("http://awareframework.com/public/pam/sad.jpg");
                moods.put("http://awareframework.com/public/pam/satisfied.jpg");
                moods.put("http://awareframework.com/public/pam/serene.jpg");
                moods.put("http://awareframework.com/public/pam/sleepy.jpg");
                moods.put("http://awareframework.com/public/pam/tense.jpg");
                moods.put("http://awareframework.com/public/pam/tired.jpg");
            }

            for (int i = 1; i < 17; i++) {
                final ImageView moodOption = (ImageView) ui.findViewById(getResources().getIdentifier("pos" + i, "id", getActivity().getPackageName()));
                while (true) {
                    Integer mood = ThreadLocalRandom.current().nextInt(0, 15+1);
                    if (!generated.contains(mood)) {
                        generated.add(mood);

                        String mood_picture_url = moods.getString(mood);

                        //Asynchronously download mood image and caches automatically
                        Ion.with(moodOption).placeholder(R.drawable.square).load(mood_picture_url);

                        moodOption.setTag(mood_picture_url);
                        moodOption.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                try {
                                    if (getExpirationThreshold() > 0 && expire_monitor != null)
                                        expire_monitor.cancel(true);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                pam_selected = view.getTag().toString();
                            }
                        });
                        break;
                    }
                }
            }

            Button cancel_text = (Button) ui.findViewById(R.id.esm_cancel);
            cancel_text.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    esm_dialog.cancel();
                }
            });

            Button submit_number = (Button) ui.findViewById(R.id.esm_submit);
            submit_number.setText(getSubmitButton());
            submit_number.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        if (getExpirationThreshold() > 0 && expire_monitor != null)
                            expire_monitor.cancel(true);

                        ContentValues rowData = new ContentValues();
                        rowData.put(ESM_Provider.ESM_Data.ANSWER_TIMESTAMP, System.currentTimeMillis());
                        rowData.put(ESM_Provider.ESM_Data.ANSWER, pam_selected);
                        rowData.put(ESM_Provider.ESM_Data.STATUS, ESM.STATUS_ANSWERED);

                        getActivity().getContentResolver().update(ESM_Provider.ESM_Data.CONTENT_URI, rowData, ESM_Provider.ESM_Data._ID + "=" + getID(), null);

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
