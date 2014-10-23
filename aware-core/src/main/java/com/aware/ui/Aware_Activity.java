package com.aware.ui;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.Aware_Preferences.StudyConfig;
import com.aware.R;
import com.aware.utils.Https;

import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class Aware_Activity extends Activity {
	
	private DrawerLayout navigationDrawer;
	private ListView navigationList;
	private ActionBarDrawerToggle navigationToggle;
	private String options[] = new String[4];
	private NavigationAdapter nav_adapter;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		navigationDrawer = (DrawerLayout) findViewById(R.id.aware_ui_main);
        navigationList = (ListView) findViewById(R.id.aware_navigation);
        
        navigationToggle = new ActionBarDrawerToggle( this, navigationDrawer, R.drawable.ic_drawer, R.string.drawer_open, R.string.drawer_close) {
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                if ( Build.VERSION.SDK_INT > 11 && getActionBar() != null ) {
                    getActionBar().setTitle(getTitle());
                    invalidateOptionsMenu();
                }
            }
            
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                if( Build.VERSION.SDK_INT > 11 && getActionBar() != null ) {
                    getActionBar().setTitle(getTitle());
                    invalidateOptionsMenu();
                }
                navigationList.requestFocus();
            }
        };
        
        navigationDrawer.setDrawerListener(navigationToggle);
        
        options[0] = "Stream";
        options[1] = "Sensors";
        options[2] = "Plugins";
        options[3] = "Studies";
        
        nav_adapter = new NavigationAdapter( this, options);
        navigationList.setAdapter(nav_adapter);
        
        navigationList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            
        	@Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            	LinearLayout item_container = (LinearLayout) view.findViewById(R.id.nav_container);
            	item_container.setBackgroundColor(Color.DKGRAY);
            	
            	for( int i=0; i< navigationList.getChildCount(); i++ ) {
            		if( i != position ) {
            			LinearLayout other = (LinearLayout) navigationList.getChildAt(i);
            			LinearLayout other_item = (LinearLayout) other.findViewById(R.id.nav_container);
                    	other_item.setBackgroundColor(Color.TRANSPARENT);
            		}
            	}
            	
            	Bundle animations = ActivityOptions.makeCustomAnimation(Aware_Activity.this, R.anim.anim_slide_in_left, R.anim.anim_slide_out_left).toBundle();
            	
            	switch( position ) {
	            	case 0: //Stream
	            		Intent stream_ui = new Intent( getApplicationContext(), Stream_UI.class);
	            		startActivity(stream_ui, animations);
	            		break;
	            	case 1: //Sensors
	            		Intent sensors_ui = new Intent( getApplicationContext(), Aware_Preferences.class );
	            		startActivity(sensors_ui, animations);
	            		break;
            		case 2: //Plugins
            			Intent plugin_manager = new Intent( getApplicationContext(), Plugins_Manager.class );
            			startActivity(plugin_manager, animations);
            			break;
	            	case 3: //Studies
//                        if( Aware.getSetting(getApplicationContext(), "study_id").length() > 0 ) {
//                            new Async_StudyData().execute(Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER));
//                        } else {
                            Intent join_study = new Intent( getApplicationContext(), CameraStudy.class );
                            startActivityForResult(join_study, Aware_Preferences.REQUEST_JOIN_STUDY, animations);
//                        }
                        break;
            	}
            	navigationDrawer.closeDrawer(navigationList);
            }
        });

        if( getActionBar() != null ) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
            getActionBar().setHomeButtonEnabled(true);
        }
	}

//    private class Async_StudyData extends AsyncTask<String, Void, JSONObject> {
//
//        private String study_url = "";
//        private ProgressDialog loader;
//
//        @Override
//        protected void onPreExecute() {
//            super.onPreExecute();
//            loader = new ProgressDialog(Aware_Activity.this);
//            loader.setTitle("Loading study");
//            loader.setMessage("Please wait...");
//            loader.setCancelable(false);
//            loader.setIndeterminate(true);
//            loader.show();
//        }
//
//        @Override
//        protected JSONObject doInBackground(String... params) {
//            study_url = params[0];
//            String study_api_key = study_url.substring(study_url.lastIndexOf("/")+1, study_url.length());
//
//            HttpResponse request = new Https(getApplicationContext()).dataGET("https://api.awareframework.com/index.php/webservice/client_get_study_info/" + study_api_key);
//            if( request != null && request.getStatusLine().getStatusCode() == 200 ) {
//                try {
//                    String json_str = EntityUtils.toString(request.getEntity());
//                    if( json_str.equals("[]") ) {
//                        return null;
//                    }
//                    JSONObject study_data = new JSONObject(json_str);
//                    return study_data;
//                } catch (ParseException e) {
//                    e.printStackTrace();
//                } catch (JSONException e) {
//                    e.printStackTrace();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//            return null;
//        }
//
//        @Override
//        protected void onPostExecute(JSONObject result) {
//            super.onPostExecute(result);
//
//            loader.dismiss();
//
//            if( result == null ) {
//                AlertDialog.Builder builder = new AlertDialog.Builder(Aware_Activity.this);
//                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        dialog.dismiss();
//                    }
//                });
//                builder.setTitle("Study information");
//                builder.setMessage("Unable to retrieve study's information. Please, try again later.");
//                builder.setNegativeButton("Quit study!", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        dialog.dismiss();
//                        Toast.makeText(getApplicationContext(), "Clearing settings... please wait", Toast.LENGTH_LONG).show();
//                        Aware.reset(getApplicationContext());
//                    }
//                });
//                builder.show();
//            } else {
//                AlertDialog.Builder builder = new AlertDialog.Builder(Aware_Activity.this);
//                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        dialog.dismiss();
//                    }
//                });
//                builder.setNegativeButton("Quit study!", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        dialog.dismiss();
//                        Toast.makeText(getApplicationContext(), "Clearing settings... please wait", Toast.LENGTH_LONG).show();
//                        Aware.reset(getApplicationContext());
//                    }
//                });
//                builder.setTitle("Study information");
//                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
//                View study_ui = inflater.inflate(R.layout.study_info, null);
//                TextView study_name = (TextView) study_ui.findViewById(R.id.study_name);
//                TextView study_description = (TextView) study_ui.findViewById(R.id.study_description);
//                TextView study_pi = (TextView) study_ui.findViewById(R.id.study_pi);
//
//                try {
//                    study_name.setText((result.getString("study_name").length()>0 ? result.getString("study_name"): "Not available"));
//                    study_description.setText((result.getString("study_description").length()>0?result.getString("study_description"):"Not available."));
//                    study_pi.setText("PI: " + result.getString("researcher_first") + " " + result.getString("researcher_last") + "\nContact: " + result.getString("researcher_contact"));
//                } catch (JSONException e) {
//                    e.printStackTrace();
//                }
//                builder.setView(study_ui);
//                builder.show();
//            }
//        }
//    }

    @Override
    protected void onResume() {
        super.onResume();

//        if( Aware.getSetting(getApplicationContext(), "study_id").length() > 0 ) {
//            new Async_StudyData().execute(Aware.getSetting(this, Aware_Preferences.WEBSERVICE_SERVER));
//        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if( requestCode == Aware_Preferences.REQUEST_JOIN_STUDY ) {
            if( resultCode == RESULT_OK ) {
            	Intent study_config = new Intent(this, StudyConfig.class);
                study_config.putExtra("study_url", data.getStringExtra("study_url"));
                startService(study_config);
            }
        }
    }
	
	@Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        navigationToggle.syncState();
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        navigationToggle.onConfigurationChanged(newConfig);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if( navigationToggle.onOptionsItemSelected(item)) return true;
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * Navigation adapter
     * @author denzil
     *
     */
    public class NavigationAdapter extends ArrayAdapter<String> {
        private final String[] items;
        private final LayoutInflater inflater;
        private final Context context;
        
        public NavigationAdapter(Context context, String[] items) {
            super(context, R.layout.aware_navigation_item, items);
            this.context = context;
            this.items = items;
            this.inflater = (LayoutInflater) context.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
        }
        
        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            LinearLayout row = (LinearLayout) inflater.inflate(R.layout.aware_navigation_item, parent, false);
            row.setFocusable(false);
            row.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Bundle animations = ActivityOptions.makeCustomAnimation(Aware_Activity.this, R.anim.anim_slide_in_left, R.anim.anim_slide_out_left).toBundle();
					switch( position ) {
		            	case 0: //Stream
		            		Intent stream_ui = new Intent( getApplicationContext(), Stream_UI.class);
		            		startActivity(stream_ui, animations);
		            		break;
		            	case 1: //Sensors
		            		Intent sensors_ui = new Intent( getApplicationContext(), Aware_Preferences.class );
		            		startActivity(sensors_ui, animations);
		            		break;
	            		case 2: //Plugins
	            			Intent plugin_manager = new Intent( getApplicationContext(), Plugins_Manager.class );
	            			startActivity(plugin_manager, animations);
	            			break;
		            	case 3: //Join study
		            		//TODO: make ui for listing available studies
//                            if( Aware.getSetting(getApplicationContext(), "study_id").length() > 0 ) {
//                                new Async_StudyData().execute(Aware.getSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SERVER));
//                            } else {
                                Intent join_study = new Intent(getApplicationContext(), CameraStudy.class);
                                startActivityForResult(join_study, Aware_Preferences.REQUEST_JOIN_STUDY, animations);
//                            }
		            		break;
	            	}
	            	navigationDrawer.closeDrawer(navigationList);
				}
			});
            ImageView nav_icon = (ImageView) row.findViewById(R.id.nav_placeholder);
            TextView nav_title = (TextView) row.findViewById(R.id.nav_title);
            
            switch( position ) {
                case 0:
                    nav_icon.setImageResource(R.drawable.ic_action_aware_stream);
                    if( context.getClass().getSimpleName().equals("Stream_UI") ) {
                    	row.setBackgroundColor(Color.DKGRAY);
                    }
                    break;
                case 1:
                    nav_icon.setImageResource(R.drawable.ic_action_aware_sensors);
                    if( context.getClass().getSimpleName().equals("Aware_Preferences")) {
                    	row.setBackgroundColor(Color.DKGRAY);
                    }
                    break;
                case 2:
                    nav_icon.setImageResource(R.drawable.ic_action_aware_plugins);
                    if( context.getClass().getSimpleName().equals("Plugins_Manager")) {
                    	row.setBackgroundColor(Color.DKGRAY);
                    }
                    break;
                case 3:
                    nav_icon.setImageResource(R.drawable.ic_action_aware_studies);
                    if( context.getClass().getSimpleName().equals("CameraStudy")) {
                    	row.setBackgroundColor(Color.DKGRAY);
                    }
                    break;
            }
            String item = items[position];
            nav_title.setText(item);
            
            return row;
        }
    }
}
