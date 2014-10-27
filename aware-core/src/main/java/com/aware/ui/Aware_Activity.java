package com.aware.ui;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
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

import com.aware.Aware_Preferences;
import com.aware.Aware_Preferences.StudyConfig;
import com.aware.R;

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
                            Intent join_study = new Intent( getApplicationContext(), CameraStudy.class );
                            startActivityForResult(join_study, Aware_Preferences.REQUEST_JOIN_STUDY, animations);
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

    @Override
    protected void onResume() {
        super.onResume();
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
