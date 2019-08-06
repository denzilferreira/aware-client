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
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.ESM;
import com.aware.R;
import com.aware.providers.ESM_Provider;
import org.json.JSONException;

/**
 * Created by denzilferreira on 21/02/16.
 */
public class ESM_Web extends ESM_Question {

    public static final String esm_url = "esm_url";

    public ESM_Web() throws JSONException {
        this.setType(ESM.TYPE_ESM_WEB);
    }

    public String getURL() throws JSONException {
        if (!this.esm.has(esm_url)) {
            this.esm.put(esm_url, "");
        }

        //add support to passing AWARE's Device ID as parameter for online surveys
        String url = esm.getString(esm_url).replace("AWARE_DEVICE_ID", Aware.getSetting(getContext(), Aware_Preferences.DEVICE_ID));
        return url;
    }

    public ESM_Web setURL(String url) throws JSONException {
        this.esm.put(esm_url, url);
        return this;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View ui = inflater.inflate(R.layout.esm_web, null);
        builder.setView(ui);

        esm_dialog = builder.create();
        esm_dialog.setCanceledOnTouchOutside(false);

        try {
            TextView esm_title = (TextView) ui.findViewById(R.id.esm_title);
            esm_title.setText(getTitle());

            TextView esm_instructions = (TextView) ui.findViewById(R.id.esm_instructions);
            esm_instructions.setText(getInstructions());

            final LinearLayout webViewContainer = ui.findViewById(R.id.esm_webpage);

            final WebView webView = new WebView(getActivity()) {
                @Override
                public boolean onCheckIsTextEditor() {
                    return true;
                }
            };
            webViewContainer.addView(webView);

            webView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View view, boolean b) {
                    try {
                        if (getExpirationThreshold() > 0 && expire_monitor != null)
                            expire_monitor.cancel(true);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
            webView.loadUrl(getURL());
            webView.setWebViewClient(new WebViewClient());

            WebSettings webSettings = webView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webSettings.setSupportZoom(true);

            Button cancel_text = (Button) ui.findViewById(R.id.esm_cancel);
            cancel_text.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    esm_dialog.cancel();
                }
            });

            Button submit_text = (Button) ui.findViewById(R.id.esm_submit);
            submit_text.setText(getSubmitButton());
            submit_text.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        if (getExpirationThreshold() > 0 && expire_monitor != null)
                            expire_monitor.cancel(true);

                        ContentValues rowData = new ContentValues();
                        rowData.put(ESM_Provider.ESM_Data.ANSWER_TIMESTAMP, System.currentTimeMillis());
                        rowData.put(ESM_Provider.ESM_Data.ANSWER, "webpage-closed");
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
