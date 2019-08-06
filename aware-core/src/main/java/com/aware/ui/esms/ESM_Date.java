package com.aware.ui.esms;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.DatePicker;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.aware.Aware;
import com.aware.ESM;
import com.aware.R;
import com.aware.providers.ESM_Provider;
import org.json.JSONException;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Created by denzil on 01/11/2016.
 */

public class ESM_Date extends ESM_Question {

    public static final String esm_calendar = "esm_calendar";

    private static Calendar datePicked = null;

    public ESM_Date() throws JSONException {
        this.setType(ESM.TYPE_ESM_DATE);
    }

    public boolean isCalendar() throws JSONException {
        if (!this.esm.has(esm_calendar)) this.esm.put(esm_calendar, false);
        return this.esm.getBoolean(esm_calendar);
    }

    public ESM_Date setCalendar(boolean isCalendar) throws JSONException {
        this.esm.put(esm_calendar, isCalendar);
        return this;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        datePicked = Calendar.getInstance();

        View ui = inflater.inflate(R.layout.esm_date, null);
        builder.setView(ui);

        esm_dialog = builder.create();
        esm_dialog.setCanceledOnTouchOutside(false);

        try {
            TextView esm_title = (TextView) ui.findViewById(R.id.esm_title);
            esm_title.setText(getTitle());

            TextView esm_instructions = (TextView) ui.findViewById(R.id.esm_instructions);
            esm_instructions.setText(getInstructions());

            final CalendarView calendarPicker = ui.findViewById(R.id.esm_calendar);
            final DatePicker datePicker = ui.findViewById(R.id.esm_datePicker);

            if (isCalendar() || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) { //date picker doesn't exist for < 21
                calendarPicker.setVisibility(View.VISIBLE);
                calendarPicker.setDate(datePicked.getTimeInMillis());
                calendarPicker.setOnClickListener(new View.OnClickListener() {
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
                calendarPicker.setOnDateChangeListener(new CalendarView.OnDateChangeListener() {
                    @Override
                    public void onSelectedDayChange(@NonNull CalendarView calendarView, int year, int month, int dayOfMonth) {
                        datePicked.set(Calendar.YEAR, year);
                        datePicked.set(Calendar.MONTH, month);
                        datePicked.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    }
                });
                datePicker.setVisibility(View.GONE);
            } else {
                datePicker.setVisibility(View.VISIBLE);
                datePicker.init(datePicked.get(Calendar.YEAR), datePicked.get(Calendar.MONTH), datePicked.get(Calendar.DAY_OF_MONTH), new DatePicker.OnDateChangedListener() {
                    @Override
                    public void onDateChanged(DatePicker datePicker, int year, int month, int dayOfMonth) {
                        datePicked.set(Calendar.YEAR, year);
                        datePicked.set(Calendar.MONTH, month);
                        datePicked.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    }
                });
                calendarPicker.setVisibility(View.GONE);
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

                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd Z");

                        ContentValues rowData = new ContentValues();
                        rowData.put(ESM_Provider.ESM_Data.ANSWER_TIMESTAMP, System.currentTimeMillis());
                        rowData.put(ESM_Provider.ESM_Data.ANSWER, dateFormat.format(datePicked.getTime()));
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
