/*
Copyright (c) 2013 AWARE Mobile Context Instrumentation Middleware/Framework
http://www.awareframework.com

AWARE is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the 
Free Software Foundation, either version 3 of the License, or (at your option) any later version (GPLv3+).

AWARE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
See the GNU General Public License for more details: http://www.gnu.org/licenses/gpl.html
*/
package com.aware.ui;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RatingBar;
import android.widget.TextView;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.ESM;
import com.aware.R;
import com.aware.providers.ESM_Provider.ESM_Data;

/**
 * Loads any ESM from the database and displays it on the screen as a system alert.
 * @author denzilferreira
 */
public class ESM_UI extends DialogFragment {
	
	private static String TAG = "AWARE::ESM UI";
	
	private static LayoutInflater inflater = null;
	private static InputMethodManager inputManager = null;
	
	private static ESMExpireMonitor expire_monitor = null;
	private static AlertDialog.Builder builder = null;
	private static Dialog current_dialog = null;
	private static Context sContext = null;
	
	private static int esm_id = 0;
	private static int esm_type = 0;
	private static int expires_seconds = 0;
	
	//Checkbox ESM UI to store selected items
	private static ArrayList<String> selected_options = new ArrayList<String>();
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		
//		getActivity().getWindow().setType(WindowManager.LayoutParams.TYPE_PRIORITY_PHONE);
//		getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
//        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        
		builder = new AlertDialog.Builder(getActivity());
		inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inputManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
		
		TAG = Aware.getSetting(getActivity().getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getActivity().getApplicationContext(), Aware_Preferences.DEBUG_TAG):TAG;
		
		Cursor visible_esm = getActivity().getContentResolver().query(ESM_Data.CONTENT_URI, null, ESM_Data.STATUS + "=" + ESM.STATUS_NEW, null, ESM_Data.TIMESTAMP + " ASC LIMIT 1");
        if( visible_esm != null && visible_esm.moveToFirst() ) {
        	esm_id = visible_esm.getInt(visible_esm.getColumnIndex(ESM_Data._ID));
        	
        	//Fixed: set the esm as not new anymore, to avoid displaying the same ESM twice due to changes in orientation
        	ContentValues update_state = new ContentValues();
        	update_state.put(ESM_Data.STATUS, ESM.STATUS_VISIBLE);
        	getActivity().getContentResolver().update(ESM_Data.CONTENT_URI, update_state, ESM_Data._ID +"="+ esm_id, null);
        	
        	esm_type = visible_esm.getInt(visible_esm.getColumnIndex(ESM_Data.TYPE));
        	expires_seconds = visible_esm.getInt(visible_esm.getColumnIndex(ESM_Data.EXPIRATION_THREASHOLD));
        	
        	builder.setTitle(visible_esm.getString(visible_esm.getColumnIndex(ESM_Data.TITLE)));
        	
        	View ui = null;
        	switch(esm_type) {
        		case ESM.TYPE_ESM_TEXT:
        			ui = inflater.inflate(R.layout.esm_text, null);
        		break;
        		case ESM.TYPE_ESM_RADIO:
        			ui = inflater.inflate(R.layout.esm_radio, null);
        		break;
        		case ESM.TYPE_ESM_CHECKBOX:
        			ui = inflater.inflate(R.layout.esm_checkbox, null);
        		break;
        		case ESM.TYPE_ESM_LIKERT:
        			ui = inflater.inflate(R.layout.esm_likert, null);
        		break;
        		case ESM.TYPE_ESM_QUICK_ANSWERS:
        			ui = inflater.inflate(R.layout.esm_quick, null);
        		break;
        	}
        	
        	final View layout = ui;
        	builder.setView(layout);
        	current_dialog = builder.create();
        	sContext = current_dialog.getContext();
        	
        	TextView esm_instructions = (TextView) layout.findViewById(R.id.esm_instructions);
        	esm_instructions.setText(visible_esm.getString(visible_esm.getColumnIndex(ESM_Data.INSTRUCTIONS)));
        	
        	switch(esm_type) {
        		case ESM.TYPE_ESM_TEXT:
        			final EditText feedback = (EditText) layout.findViewById(R.id.esm_feedback);
                	Button cancel_text = (Button) layout.findViewById(R.id.esm_cancel);
                    cancel_text.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							inputManager.hideSoftInputFromWindow(feedback.getWindowToken(), 0);
							current_dialog.cancel();
						}
					});
                    Button submit_text = (Button) layout.findViewById(R.id.esm_submit);
                    submit_text.setText(visible_esm.getString(visible_esm.getColumnIndex(ESM_Data.SUBMIT)));
                    submit_text.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							
							inputManager.hideSoftInputFromWindow(feedback.getWindowToken(), 0);
							
							if( expires_seconds > 0 && expire_monitor != null ) expire_monitor.cancel(true);
							
							ContentValues rowData = new ContentValues();
		                    rowData.put(ESM_Data.ANSWER_TIMESTAMP, System.currentTimeMillis());
							rowData.put(ESM_Data.ANSWER, feedback.getText().toString());
							rowData.put(ESM_Data.STATUS, ESM.STATUS_ANSWERED);
		                    
		                    sContext.getContentResolver().update(ESM_Data.CONTENT_URI, rowData, ESM_Data._ID + "=" + esm_id, null);    
		                    
		                    Intent answer = new Intent(ESM.ACTION_AWARE_ESM_ANSWERED);
		                    getActivity().sendBroadcast(answer);
		                    
		                    if(Aware.DEBUG) Log.d(TAG,"Answer:" + rowData.toString());
		                    
		                    current_dialog.dismiss();
						}
					});	
        		break;
        		case ESM.TYPE_ESM_RADIO:
        			try {
	        			final RadioGroup radioOptions = (RadioGroup) layout.findViewById(R.id.esm_radio);
	        			final JSONArray radios = new JSONArray(visible_esm.getString(visible_esm.getColumnIndex(ESM_Data.RADIOS)));
					    
	                    for(int i=0; i<radios.length(); i++) {
	                        final RadioButton radioOption = new RadioButton(getActivity());
	                        radioOption.setId(i);
	                        radioOption.setText(radios.getString(i));
	                        radioOptions.addView(radioOption);
	                        
	                        if( radios.getString(i).equals("Other") ) {
	                            radioOption.setOnClickListener(new View.OnClickListener() {
	                                @Override
	                                public void onClick(View v) {
	                                    final Dialog editOther = new Dialog(getActivity());
	                                	editOther.setTitle("Can you be more specific, please?");
	                                	editOther.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
	                                	editOther.getWindow().setGravity(Gravity.TOP);
                                        editOther.getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
	                                	
	                                	LinearLayout editor = new LinearLayout(getActivity());
	                                    editor.setOrientation(LinearLayout.VERTICAL);
	                                    
	                                    editOther.setContentView(editor);
	                                    editOther.show();
	                                    
	                                    final EditText otherText = new EditText(getActivity());
	                                    editor.addView(otherText);
	                                    
	                                    Button confirm = new Button(getActivity());
	                                    confirm.setText("OK");
	                                    confirm.setOnClickListener(new View.OnClickListener() {
	                                        @Override
	                                        public void onClick(View v) {
	                                        	if(otherText.length() > 0 ) radioOption.setText(otherText.getText());
	                                        	inputManager.hideSoftInputFromWindow(otherText.getWindowToken(), 0);
	                                            editOther.dismiss();
	                                        }
	                                    });
	                                    editor.addView(confirm);
	                                }
	                            });
	                        }
	                    }
	                    Button cancel_radio = (Button) layout.findViewById(R.id.esm_cancel);
	                    cancel_radio.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								current_dialog.cancel();
							}
						});
	                    Button submit_radio = (Button) layout.findViewById(R.id.esm_submit);
	                    submit_radio.setText(visible_esm.getString(visible_esm.getColumnIndex(ESM_Data.SUBMIT)));
	                    submit_radio.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								
								if( expires_seconds > 0 && expire_monitor != null ) expire_monitor.cancel(true);
								
								ContentValues rowData = new ContentValues();
			                    rowData.put(ESM_Data.ANSWER_TIMESTAMP, System.currentTimeMillis());
			                    
	                    		RadioGroup radioOptions = (RadioGroup) layout.findViewById(R.id.esm_radio);
	                    		if( radioOptions.getCheckedRadioButtonId() != -1 ) {
	                    			RadioButton selected = (RadioButton) radioOptions.getChildAt(radioOptions.getCheckedRadioButtonId());
	                    			rowData.put(ESM_Data.ANSWER, selected.getText().toString());
	                    		}
	                    	    rowData.put(ESM_Data.STATUS, ESM.STATUS_ANSWERED);
			                    
			                    sContext.getContentResolver().update(ESM_Data.CONTENT_URI, rowData, ESM_Data._ID + "=" + esm_id, null);    
			                    
			                    Intent answer = new Intent(ESM.ACTION_AWARE_ESM_ANSWERED);
			                    getActivity().sendBroadcast(answer);
			                    
			                    if(Aware.DEBUG) Log.d(TAG,"Answer:" + rowData.toString());
			                    
			                    current_dialog.dismiss();
							}
						});
        			} catch (JSONException e) {
    					e.printStackTrace();
    				}
        		break;
        		case ESM.TYPE_ESM_CHECKBOX:
        			try {
	        			final LinearLayout checkboxes = (LinearLayout) layout.findViewById(R.id.esm_checkboxes);
	        			final JSONArray checks = new JSONArray(visible_esm.getString(visible_esm.getColumnIndex(ESM_Data.CHECKBOXES)));
					    
	                    for(int i=0; i<checks.length(); i++) {
	                        final CheckBox checked = new CheckBox(getActivity());
	                        checked.setText(checks.getString(i));
	                        checked.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
	                            @Override
	                            public void onCheckedChanged(final CompoundButton buttonView, boolean isChecked) {
	                                if( isChecked ) {
	                                    if( buttonView.getText().equals("Other") ) {
	                                        checked.setOnClickListener(new View.OnClickListener() {
	                                            @Override
	                                            public void onClick(View v) {
	                                            	final Dialog editOther = new Dialog(getActivity());
	        	                                	editOther.setTitle("Can you be more specific, please?");
	        	                                	editOther.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
	        	                                	editOther.getWindow().setGravity(Gravity.TOP);
	        	                                	editOther.getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
	        	                                	
	                                            	LinearLayout editor = new LinearLayout(getActivity());
	                                                editor.setOrientation(LinearLayout.VERTICAL);
	                                                editOther.setContentView(editor);
	                                                editOther.show();
	                                                
	                                                final EditText otherText = new EditText(getActivity());
	                                                editor.addView(otherText);
	                                                
	                                                Button confirm = new Button(getActivity());
	                                                confirm.setText("OK");
	                                                confirm.setOnClickListener(new View.OnClickListener() {
	                                                    @Override
	                                                    public void onClick(View v) {
	                                                        if( otherText.length() > 0 ) {
	                                                        	inputManager.hideSoftInputFromWindow(otherText.getWindowToken(), 0);
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
	                                    }else {
	                                    	selected_options.add(buttonView.getText().toString());
	                                    }
	                                } else {
	                                    selected_options.remove(buttonView.getText().toString());
	                                }
	                            }
	                        });
	                        checkboxes.addView(checked);
	                    }
	                    Button cancel_checkbox = (Button) layout.findViewById(R.id.esm_cancel);
	                    cancel_checkbox.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								current_dialog.cancel();
							}
						});
	                    Button submit_checkbox = (Button) layout.findViewById(R.id.esm_submit);
	                    submit_checkbox.setText(visible_esm.getString(visible_esm.getColumnIndex(ESM_Data.SUBMIT)));
	                    submit_checkbox.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								
								if( expires_seconds > 0 && expire_monitor != null ) expire_monitor.cancel(true);
								
								ContentValues rowData = new ContentValues();
			                    rowData.put(ESM_Data.ANSWER_TIMESTAMP, System.currentTimeMillis());
			                
			                    if( selected_options.size() > 0 ){
	                    			rowData.put(ESM_Data.ANSWER, selected_options.toString());
	                    		}
	                		    
			                    rowData.put(ESM_Data.STATUS, ESM.STATUS_ANSWERED);
			                    
			                    sContext.getContentResolver().update(ESM_Data.CONTENT_URI, rowData, ESM_Data._ID + "=" + esm_id, null);    
			                    
			                    Intent answer = new Intent(ESM.ACTION_AWARE_ESM_ANSWERED);
			                    getActivity().sendBroadcast(answer);
			                    
			                    if(Aware.DEBUG) Log.d(TAG,"Answer:" + rowData.toString());
			                    
			                    current_dialog.dismiss();
							}
						});
        			} catch (JSONException e) {
    					e.printStackTrace();
    				}
    			break;
        		case ESM.TYPE_ESM_LIKERT:
        			final RatingBar ratingBar = (RatingBar) layout.findViewById(R.id.esm_likert);
                    ratingBar.setMax(visible_esm.getInt(visible_esm.getColumnIndex(ESM_Data.LIKERT_MAX)));
                    ratingBar.setStepSize((float) visible_esm.getDouble(visible_esm.getColumnIndex(ESM_Data.LIKERT_STEP)));
                    ratingBar.setNumStars(visible_esm.getInt(visible_esm.getColumnIndex(ESM_Data.LIKERT_MAX)));
                    
                    TextView min_label = (TextView) layout.findViewById(R.id.esm_min);
                    min_label.setText(visible_esm.getString(visible_esm.getColumnIndex(ESM_Data.LIKERT_MIN_LABEL)));
                    
                    TextView max_label = (TextView) layout.findViewById(R.id.esm_max);
                    max_label.setText(visible_esm.getString(visible_esm.getColumnIndex(ESM_Data.LIKERT_MAX_LABEL)));
                    
                    Button cancel = (Button) layout.findViewById(R.id.esm_cancel);
                    cancel.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							current_dialog.cancel();
						}
					});
                    Button submit = (Button) layout.findViewById(R.id.esm_submit);
                    submit.setText(visible_esm.getString(visible_esm.getColumnIndex(ESM_Data.SUBMIT)));
                    submit.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							if( expires_seconds > 0 && expire_monitor != null ) expire_monitor.cancel(true);
							
							ContentValues rowData = new ContentValues();
		                    rowData.put(ESM_Data.ANSWER_TIMESTAMP, System.currentTimeMillis());
		                    rowData.put(ESM_Data.ANSWER, ratingBar.getRating());
                	        rowData.put(ESM_Data.STATUS, ESM.STATUS_ANSWERED);
		                    
		                    sContext.getContentResolver().update(ESM_Data.CONTENT_URI, rowData, ESM_Data._ID + "=" + esm_id, null);    
		                    
		                    Intent answer = new Intent(ESM.ACTION_AWARE_ESM_ANSWERED);
		                    getActivity().sendBroadcast(answer);
		                    
		                    if(Aware.DEBUG) Log.d(TAG,"Answer:" + rowData.toString());
		                    
		                    current_dialog.dismiss();
						}
					});
    			break;
        		case ESM.TYPE_ESM_QUICK_ANSWERS:
					try {
						final JSONArray answers = new JSONArray(visible_esm.getString(visible_esm.getColumnIndex(ESM_Data.QUICK_ANSWERS)));
					    final LinearLayout answersHolder = (LinearLayout) layout.findViewById(R.id.esm_answers);
					    
					    //If we have more than 3 possibilities, better that the UI is vertical for UX
                        if( answers.length() > 3 ) {
                        	answersHolder.setOrientation(LinearLayout.VERTICAL);
                        }
                        
                        for(int i=0; i<answers.length(); i++) {
                            final Button answer = new Button(getActivity());
                            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1.0f );
                            answer.setLayoutParams(params);
                            answer.setText(answers.getString(i));
                            answer.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                	
                                	if( expires_seconds > 0 && expire_monitor != null ) expire_monitor.cancel(true);
                                	
                                    ContentValues rowData = new ContentValues();
                                    rowData.put(ESM_Data.ANSWER_TIMESTAMP, System.currentTimeMillis());
                                    rowData.put(ESM_Data.STATUS, ESM.STATUS_ANSWERED);
                                    rowData.put(ESM_Data.ANSWER, (String) answer.getText());
                                    
                                    sContext.getContentResolver().update(ESM_Data.CONTENT_URI, rowData, ESM_Data._ID + "=" + esm_id, null);
                                    
                                    Intent answer = new Intent(ESM.ACTION_AWARE_ESM_ANSWERED);
        		                    getActivity().sendBroadcast(answer);
        		                    
        		                    if(Aware.DEBUG) Log.d(TAG,"Answer:" + rowData.toString());
                                    
                                    current_dialog.dismiss();
                                }
                            });
                            answersHolder.addView(answer);
                        }
					} catch (JSONException e) {
						e.printStackTrace();
					}
    			break;
        	}
        }
        if( visible_esm != null && ! visible_esm.isClosed() ) visible_esm.close();
        
        //Start dialog visibility threshold
        if( expires_seconds > 0 ) {
            expire_monitor = new ESMExpireMonitor( System.currentTimeMillis(), expires_seconds, esm_id );
            expire_monitor.execute();
        }
        
        //Fixed: doesn't dismiss the dialog if touched outside or ghost touches
        current_dialog.setCanceledOnTouchOutside(false);
        
        return current_dialog;
	}

	@Override
	public void onCancel(DialogInterface dialog) {
		super.onCancel(dialog);
		
		if( expires_seconds > 0 && expire_monitor != null ) expire_monitor.cancel(true);
		
		ContentValues rowData = new ContentValues();
        rowData.put(ESM_Data.ANSWER_TIMESTAMP, System.currentTimeMillis());
        rowData.put(ESM_Data.STATUS, ESM.STATUS_DISMISSED);
        sContext.getContentResolver().update(ESM_Data.CONTENT_URI, rowData, ESM_Data._ID + "=" + esm_id, null);
        
        Intent answer = new Intent(ESM.ACTION_AWARE_ESM_DISMISSED);
        sContext.sendBroadcast(answer);
	}
	
	@Override
	public void onDismiss(DialogInterface dialog) {
		super.onDismiss(dialog);
		if( expires_seconds > 0 && expire_monitor != null ) expire_monitor.cancel(true);
	}
	
	/**
     * Checks on the background if the current visible dialog has expired or not. If it did, removes dialog and updates the status to expired.
     * @author denzil
     *
     */
    private class ESMExpireMonitor extends AsyncTask<Void, Void, Void> {
    	private long display_timestamp = 0;
    	private int expires_in_seconds = 0;
    	private int esm_id = 0;
    	
    	public ESMExpireMonitor(long display_timestamp, int expires_in_seconds, int esm_id) {
			this.display_timestamp = display_timestamp;
			this.expires_in_seconds = expires_in_seconds;
			this.esm_id = esm_id;
		}
    	
        @Override
        protected Void doInBackground(Void... params) {
        	while( (System.currentTimeMillis()-display_timestamp) / 1000 <= expires_in_seconds ) {
                if( isCancelled() ) {
                	Cursor esm = sContext.getContentResolver().query(ESM_Data.CONTENT_URI, null, ESM_Data._ID + "=" + esm_id, null, null );
                	if( esm != null && esm.moveToFirst() ) {
                		int status = esm.getInt(esm.getColumnIndex(ESM_Data.STATUS));
                		switch(status) {
                			case ESM.STATUS_ANSWERED:
                				if( Aware.DEBUG ) Log.d(TAG,"ESM has been answered!");
                			break;
                			case ESM.STATUS_DISMISSED:
                				if( Aware.DEBUG ) Log.d(TAG,"ESM has been dismissed!");
                			break;
                		}
                	}
                	if( esm != null && ! esm.isClosed() ) esm.close();
                	return null;
                }
            }
        	
        	if( Aware.DEBUG ) Log.d(TAG,"ESM has expired!");
        	
            ContentValues rowData = new ContentValues();
            rowData.put(ESM_Data.ANSWER_TIMESTAMP, System.currentTimeMillis());
            rowData.put(ESM_Data.STATUS, ESM.STATUS_EXPIRED);
            sContext.getContentResolver().update(ESM_Data.CONTENT_URI, rowData, ESM_Data._ID + "=" + esm_id, null);
            
            Intent expired = new Intent(ESM.ACTION_AWARE_ESM_EXPIRED);
        	sContext.sendBroadcast(expired);
            
            current_dialog.dismiss();
            return null;
        }
    }
}