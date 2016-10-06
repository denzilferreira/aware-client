package com.aware.phone.ui;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;

import com.aware.phone.R;

import org.json.JSONException;
import org.json.JSONObject;

public class Aware_Join_Study extends Aware_Activity {

    private String study_url;
    private JSONObject study_json;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.aware_join_study);

        TextView txtStudyTitle = (TextView) findViewById(R.id.txt_title);
        TextView txtStudyDescription = (TextView) findViewById(R.id.txt_description);
        TextView txtStudyResearcher = (TextView) findViewById(R.id.txt_researcher);

        study_url = getIntent().getStringExtra("study_url");

        //TODO load info directly from database Aware_Studies. Use Aware.getStudy(context, study_url)
//        try {
//            study_json = new JSONObject(getIntent().getStringExtra("study_json"));
//            txtStudyTitle.setText((study_json.getString("study_name").length() > 0 ? study_json.getString("study_name") : "Not available"));
//            txtStudyDescription.setText((study_json.getString("study_description").length() > 0 ? study_json.getString("study_description") : "Not available."));
//            txtStudyResearcher.setText("PI: " + study_json.getString("researcher_first") + " " + study_json.getString("researcher_last") + "\nContact: " + study_json.getString("researcher_contact"));
//        } catch (JSONException e) {
//            e.printStackTrace();
//        }



    }
}
