package com.aware.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RatingBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.ESM;
import com.aware.R;
import com.aware.providers.ESM_Provider.ESM_Data;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;

/**
 * Loads any ESM from the database and displays it on the screen as a system alert.
 * @author denzilferreira
 */
public class ESM_UI extends DialogFragment {

	private static String TAG = "AWARE::ESM UI";

	private static ESMExpireMonitor expire_monitor = null;
	private static Dialog current_dialog = null;
	private static Context sContext = null;

	private static int esm_id = 0;
	private static int esm_type = 0;
	private static int expires_seconds = 0;

	private static int selected_scale_progress = -1;

	//Checkbox ESM UI to store selected items
	private static ArrayList<String> selected_options = new ArrayList<String>();

    @NonNull
    @Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		TAG = Aware.getSetting(getActivity().getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getActivity().getApplicationContext(), Aware_Preferences.DEBUG_TAG):TAG;

        Cursor visible_esm;
        if( ESM.isESMVisible(getActivity().getApplicationContext()) ) {
            visible_esm = getActivity().getContentResolver().query(ESM_Data.CONTENT_URI, null, ESM_Data.STATUS + "=" + ESM.STATUS_VISIBLE, null, ESM_Data.TIMESTAMP + " ASC LIMIT 1");
        } else {
            visible_esm = getActivity().getContentResolver().query(ESM_Data.CONTENT_URI, null, ESM_Data.STATUS + "=" + ESM.STATUS_NEW, null, ESM_Data.TIMESTAMP + " ASC LIMIT 1");
        }
        if( visible_esm != null && visible_esm.moveToFirst() ) {
        	esm_id = visible_esm.getInt(visible_esm.getColumnIndex(ESM_Data._ID));

        	//Fixed: set the esm as VISIBLE, to avoid displaying the same ESM twice due to changes in orientation
        	ContentValues update_state = new ContentValues();
        	update_state.put(ESM_Data.STATUS, ESM.STATUS_VISIBLE);
        	getActivity().getContentResolver().update(ESM_Data.CONTENT_URI, update_state, ESM_Data._ID +"="+ esm_id, null);
            //--

        	esm_type = visible_esm.getInt(visible_esm.getColumnIndex(ESM_Data.TYPE));
        	expires_seconds = visible_esm.getInt(visible_esm.getColumnIndex(ESM_Data.EXPIRATION_THRESHOLD));

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
				case ESM.TYPE_ESM_SCALE:
					ui = inflater.inflate(R.layout.esm_scale, null);
				break;
        	}

        	final View layout = ui;
            builder.setView(layout);
        	current_dialog = builder.create();
        	sContext = current_dialog.getContext();

        	TextView esm_instructions = (TextView) layout.findViewById(R.id.esm_instructions);
            if( esm_instructions != null ) {
                esm_instructions.setText(visible_esm.getString(visible_esm.getColumnIndex(ESM_Data.INSTRUCTIONS)));
            }

        	switch(esm_type) {
        		case ESM.TYPE_ESM_TEXT:
        			final EditText feedback = (EditText) layout.findViewById(R.id.esm_feedback);
					feedback.requestFocus();
					current_dialog.getWindow().setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_VISIBLE);

                    feedback.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if( expires_seconds > 0 && expire_monitor != null ) expire_monitor.cancel(true);
                        }
                    });
                	Button cancel_text = (Button) layout.findViewById(R.id.esm_cancel);
                    cancel_text.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							current_dialog.cancel();
						}
					});
                    Button submit_text = (Button) layout.findViewById(R.id.esm_submit);
                    submit_text.setText(visible_esm.getString(visible_esm.getColumnIndex(ESM_Data.SUBMIT)));
                    submit_text.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {

                            if( expires_seconds > 0 && expire_monitor != null ) expire_monitor.cancel(true);

							ContentValues rowData = new ContentValues();
		                    rowData.put(ESM_Data.ANSWER_TIMESTAMP, System.currentTimeMillis());
							rowData.put(ESM_Data.ANSWER, feedback.getText().toString());
							rowData.put(ESM_Data.STATUS, ESM.STATUS_ANSWERED);

		                    sContext.getContentResolver().update(ESM_Data.CONTENT_URI, rowData, ESM_Data._ID + "=" + esm_id, null);

		                    Intent answer = new Intent(ESM.ACTION_AWARE_ESM_ANSWERED);
		                    getActivity().sendBroadcast(answer);

		                    if(Aware.DEBUG) Log.d(TAG,"Answer:" + rowData.toString());

							if( current_dialog != null ) current_dialog.dismiss();
						}
					});
        		break;
        		case ESM.TYPE_ESM_RADIO:
        			try {
	        			final RadioGroup radioOptions = (RadioGroup) layout.findViewById(R.id.esm_radio);
						radioOptions.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								if( expires_seconds > 0 && expire_monitor != null ) expire_monitor.cancel(true);
							}
						});
	        			final JSONArray radios = new JSONArray(visible_esm.getString(visible_esm.getColumnIndex(ESM_Data.RADIOS)));

	                    for(int i=0; i<radios.length(); i++) {
	                        final RadioButton radioOption = new RadioButton(getActivity());
	                        radioOption.setId(i);
	                        radioOption.setText(" " + radios.getString(i));
	                        radioOptions.addView(radioOption);

	                        if( radios.getString(i).equals( getResources().getString(R.string.aware_esm_other) ) ) {
	                            radioOption.setOnClickListener(new View.OnClickListener() {
	                                @Override
	                                public void onClick(View v) {
	                                    final Dialog editOther = new Dialog(getActivity());
	                                	editOther.setTitle(getResources().getString(R.string.aware_esm_other_follow));
	                                	editOther.getWindow().setGravity(Gravity.TOP);
                                        editOther.getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

	                                	LinearLayout editor = new LinearLayout(getActivity());
	                                    editor.setOrientation(LinearLayout.VERTICAL);

	                                    editOther.setContentView(editor);
	                                    editOther.show();

	                                    final EditText otherText = new EditText(getActivity());
										otherText.setHint(getResources().getString(R.string.aware_esm_other_follow));
	                                    editor.addView(otherText);
										otherText.requestFocus();
										editOther.getWindow().setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_VISIBLE);

	                                    Button confirm = new Button(getActivity());
	                                    confirm.setText("OK");
	                                    confirm.setOnClickListener(new View.OnClickListener() {
	                                        @Override
	                                        public void onClick(View v) {
	                                        	if(otherText.length() > 0 ) radioOption.setText(otherText.getText());
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

								if( current_dialog != null ) current_dialog.dismiss();
							}
						});
        			} catch (JSONException e) {
    					e.printStackTrace();
    				}
        		break;
        		case ESM.TYPE_ESM_CHECKBOX:
        			try {
	        			final LinearLayout checkboxes = (LinearLayout) layout.findViewById(R.id.esm_checkboxes);
                        checkboxes.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if( expires_seconds > 0 && expire_monitor != null ) expire_monitor.cancel(true);
                            }
                        });
	        			final JSONArray checks = new JSONArray(visible_esm.getString(visible_esm.getColumnIndex(ESM_Data.CHECKBOXES)));

	                    for(int i=0; i<checks.length(); i++) {
	                        final CheckBox checked = new CheckBox(getActivity());
	                        checked.setText(" " + checks.getString(i));
	                        checked.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
	                            @Override
	                            public void onCheckedChanged(final CompoundButton buttonView, boolean isChecked) {
                                    if( isChecked ) {
	                                    if( buttonView.getText().equals(getResources().getString(R.string.aware_esm_other)) ) {
	                                        checked.setOnClickListener(new View.OnClickListener() {
	                                            @Override
	                                            public void onClick(View v) {
	                                            	final Dialog editOther = new Dialog(getActivity());
	        	                                	editOther.setTitle(getResources().getString(R.string.aware_esm_other_follow));
	        	                                	editOther.getWindow().setGravity(Gravity.TOP);
	        	                                	editOther.getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

	                                            	LinearLayout editor = new LinearLayout(getActivity());
	                                                editor.setOrientation(LinearLayout.VERTICAL);
	                                                editOther.setContentView(editor);
	                                                editOther.show();

	                                                final EditText otherText = new EditText(getActivity());
													otherText.setHint(getResources().getString(R.string.aware_esm_other_follow));
	                                                editor.addView(otherText);
													otherText.requestFocus();
                                                    editOther.getWindow().setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_VISIBLE);

	                                                Button confirm = new Button(getActivity());
	                                                confirm.setText("OK");
	                                                confirm.setOnClickListener(new View.OnClickListener() {
	                                                    @Override
	                                                    public void onClick(View v) {
	                                                        if( otherText.length() > 0 ) {
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
			                    selected_options.clear();

			                    Intent answer = new Intent(ESM.ACTION_AWARE_ESM_ANSWERED);
			                    getActivity().sendBroadcast(answer);

			                    if(Aware.DEBUG) Log.d(TAG,"Answer: " + rowData.toString());

								if( current_dialog != null ) current_dialog.dismiss();
							}
						});
        			} catch (JSONException e) {
    					e.printStackTrace();
    				}
    			break;
        		case ESM.TYPE_ESM_LIKERT:
        			final RatingBar ratingBar = (RatingBar) layout.findViewById(R.id.esm_likert);
                    ratingBar.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if( expires_seconds > 0 && expire_monitor != null ) expire_monitor.cancel(true);
                        }
                    });
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

							if( current_dialog != null ) current_dialog.dismiss();
						}
					});
    			break;
				case ESM.TYPE_ESM_SCALE:

                    final int min_value = visible_esm.getInt(visible_esm.getColumnIndex(ESM_Data.SCALE_MIN));
                    final int max_value = visible_esm.getInt(visible_esm.getColumnIndex(ESM_Data.SCALE_MAX));

                    selected_scale_progress = visible_esm.getInt(visible_esm.getColumnIndex(ESM_Data.SCALE_START));

                    final int step_size = visible_esm.getInt(visible_esm.getColumnIndex(ESM_Data.SCALE_STEP));

                    final TextView current_slider_value = (TextView) layout.findViewById(R.id.esm_slider_value);
					current_slider_value.setText( "" + selected_scale_progress);

					final SeekBar seekBar = (SeekBar) layout.findViewById(R.id.esm_scale);
                    seekBar.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if( expires_seconds > 0 && expire_monitor != null ) expire_monitor.cancel(true);
                        }
                    });

                    seekBar.incrementProgressBy(step_size);

                    if( min_value >= 0 ) {
                        seekBar.setProgress( selected_scale_progress );
                        seekBar.setMax( max_value );
                    } else {
                        seekBar.setMax( max_value*2 );
                        seekBar.setProgress( max_value ); //move handle to center value
                    }
                    current_slider_value.setText( "" + selected_scale_progress );

                    seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            if( fromUser ) {
                                if( min_value < 0 ) {
                                    progress -= max_value;
                                }

                                progress /= step_size;
                                progress *= step_size;

                                selected_scale_progress = progress;

                                if( selected_scale_progress < min_value ) {
                                    selected_scale_progress = min_value;
                                } else if( selected_scale_progress > max_value ) {
                                    selected_scale_progress = max_value;
                                }

                                current_slider_value.setText( "" + selected_scale_progress );
                            }
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {
                            current_slider_value.setText( "" + selected_scale_progress );
                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {
                            current_slider_value.setText( "" + selected_scale_progress );
                        }
                    });

					TextView min_scale_label = (TextView) layout.findViewById(R.id.esm_min);
					min_scale_label.setText(visible_esm.getString(visible_esm.getColumnIndex(ESM_Data.SCALE_MIN_LABEL)));

					TextView max_scale_label = (TextView) layout.findViewById(R.id.esm_max);
					max_scale_label.setText(visible_esm.getString(visible_esm.getColumnIndex(ESM_Data.SCALE_MAX_LABEL)));

					Button scale_cancel = (Button) layout.findViewById(R.id.esm_cancel);
					scale_cancel.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							current_dialog.cancel();
						}
					});
					Button scale_submit = (Button) layout.findViewById(R.id.esm_submit);
					scale_submit.setText(visible_esm.getString(visible_esm.getColumnIndex(ESM_Data.SUBMIT)));
                    scale_submit.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {

                            if( expires_seconds > 0 && expire_monitor != null ) expire_monitor.cancel(true);

							ContentValues rowData = new ContentValues();
							rowData.put(ESM_Data.ANSWER_TIMESTAMP, System.currentTimeMillis());
							rowData.put(ESM_Data.ANSWER, selected_scale_progress );
							rowData.put(ESM_Data.STATUS, ESM.STATUS_ANSWERED);

							sContext.getContentResolver().update(ESM_Data.CONTENT_URI, rowData, ESM_Data._ID + "=" + esm_id, null);

							Intent answer = new Intent(ESM.ACTION_AWARE_ESM_ANSWERED);
							getActivity().sendBroadcast(answer);

							if(Aware.DEBUG) Log.d(TAG,"Answer:" + rowData.toString());

							if( current_dialog != null ) current_dialog.dismiss();
						}
					});
					break;
        		case ESM.TYPE_ESM_QUICK_ANSWERS:
					try {
						final JSONArray answers = new JSONArray(visible_esm.getString(visible_esm.getColumnIndex(ESM_Data.QUICK_ANSWERS)));
						final LinearLayout answersHolder = (LinearLayout) layout.findViewById(R.id.esm_answers);

						//If we have more than 3 possibilities, use a vertical layout for UX
						if( answers.length() > 3 ) {
							answersHolder.setOrientation(LinearLayout.VERTICAL);
						}

						for(int i=0; i<answers.length(); i++) {
							final Button answer = new Button(getActivity());
							LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1.0f );
							//Fixed: buttons now of the same height regardless of content.
							params.height = LayoutParams.MATCH_PARENT;
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

									if( current_dialog != null ) current_dialog.dismiss();
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
		dismissESM();
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		super.onDismiss(dialog);
		if( expires_seconds > 0 && expire_monitor != null ) expire_monitor.cancel(true);
	}

	@Override
	public void onPause() {
		super.onPause();

        if( ESM.isESMVisible(getActivity().getApplicationContext()) ) {
            if( Aware.DEBUG ) Log.d(TAG, "ESM was visible, go back to notification bar");

            //Revert to NEW state
            ContentValues rowData = new ContentValues();
            rowData.put(ESM_Data.ANSWER_TIMESTAMP, System.currentTimeMillis());
            rowData.put(ESM_Data.STATUS, ESM.STATUS_NEW);
            sContext.getContentResolver().update(ESM_Data.CONTENT_URI, rowData, ESM_Data._ID + "=" + esm_id, null);

            //Update notification
            ESM.notifyESM(getActivity().getApplicationContext());

            current_dialog.dismiss();
            getActivity().finish();
        }
	}

    /**
     * When dismissing one ESM by pressing cancel, the rest of the queue gets dismissed
     */
	private void dismissESM() {
        ContentValues rowData = new ContentValues();
        rowData.put(ESM_Data.ANSWER_TIMESTAMP, System.currentTimeMillis());
        rowData.put(ESM_Data.STATUS, ESM.STATUS_DISMISSED);
        sContext.getContentResolver().update(ESM_Data.CONTENT_URI, rowData, ESM_Data._ID + "=" + esm_id, null);

        Cursor esm = sContext.getContentResolver().query(ESM_Data.CONTENT_URI, null, ESM_Data.STATUS + " IN (" + ESM.STATUS_NEW + "," + ESM.STATUS_VISIBLE + ")", null, null);
        if( esm != null && esm.moveToFirst() ) {
            do {
                rowData = new ContentValues();
                rowData.put(ESM_Data.ANSWER_TIMESTAMP, System.currentTimeMillis());
                rowData.put(ESM_Data.STATUS, ESM.STATUS_DISMISSED);
                sContext.getContentResolver().update(ESM_Data.CONTENT_URI, rowData, null, null);
            } while(esm.moveToNext());
        }
        if( esm != null && ! esm.isClosed()) esm.close();

        Intent answer = new Intent(ESM.ACTION_AWARE_ESM_DISMISSED);
        sContext.sendBroadcast(answer);

		if( current_dialog != null ) current_dialog.dismiss();
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

			if( current_dialog != null ) current_dialog.dismiss();

			return null;
		}
	}
}