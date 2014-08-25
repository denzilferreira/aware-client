/**
@author: denzil
*/
package com.aware.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.aware.Aware;
import com.aware.R;
import com.aware.utils.Https;

import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class CameraStudy extends Aware_Activity implements PreviewCallback, AutoFocusCallback {
    
    private Camera mCamera;
    private CameraPreview mPreview;
    private Handler autoFocusHandler;
    
    private FrameLayout camera_preview;
    private ImageScanner scanner;
    
    private boolean is_previewing = true;
    
    static {
        System.loadLibrary("iconv");
        System.loadLibrary("zbarjni");
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	setContentView(R.layout.aware_study_join);
    	
    	super.onCreate(savedInstanceState);
        
        lockCamera();
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	lockCamera();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }
    
    private void lockCamera() {
    	if( mCamera != null ) return;
    	
    	setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    	is_previewing = true;
        autoFocusHandler = new Handler();
        mCamera = getCameraInstance();
        
        scanner = new ImageScanner();
        scanner.setConfig(0, Config.X_DENSITY, 3);
        scanner.setConfig(0, Config.Y_DENSITY, 3);
        
        mPreview = new CameraPreview(this, mCamera, this, this);
        camera_preview = (FrameLayout) findViewById(R.id.camera_view);
        camera_preview.addView(mPreview);
    }
    
    private void releaseCamera() {
        if( mCamera != null ) {
            is_previewing = false;
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
            camera_preview.removeView(mPreview);
        }
    }
    
    private static Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open();
        }catch(Exception e) {
            
        }
        return c;
    }
    
    private Runnable doAutoFocus = new Runnable() {
        @Override
        public void run() {
            if( is_previewing ) mCamera.autoFocus(CameraStudy.this);
        }
    };
    
    @Override
    public void onAutoFocus(boolean success, Camera camera) {
        autoFocusHandler.postDelayed(doAutoFocus, 1000);
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        Camera.Parameters params = camera.getParameters();
        Size size = params.getPreviewSize();
        
        Image barcode = new Image(size.width, size.height, "Y800");
        barcode.setData(data);
        
        int result = scanner.scanImage(barcode);
        if( result != 0 ) {
            is_previewing = false;
            
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            
            SymbolSet syms = scanner.getResults();
            String scanned = "";
            for(Symbol sym : syms ) {
                scanned += sym.getData();
            }
            
            if( Aware.DEBUG ) Log.d(Aware.TAG, "User is joining new study...");
            new StudyData().execute(scanned);
        }
    }
    
    private class StudyData extends AsyncTask<String, Void, JSONObject> {

    	private String study_url = "";
    	private ProgressDialog loader;
    	
    	@Override
    	protected void onPreExecute() {
    		super.onPreExecute();
    		loader = new ProgressDialog(CameraStudy.this);
    		loader.setTitle("Loading study");
    		loader.setMessage("Please wait...");
    		loader.setCancelable(false);
    		loader.setIndeterminate(true);
    		loader.show();
    	}
    	
		@Override
		protected JSONObject doInBackground(String... params) {
			study_url = params[0];
			String study_api_key = study_url.substring(study_url.lastIndexOf("/")+1, study_url.length());

			HttpResponse request = new Https(getApplicationContext()).dataGET("https://api.awareframework.com/index.php/webservice/client_get_study_info/" + study_api_key);
			if( request != null && request.getStatusLine().getStatusCode() == 200 ) {
				try {
                    String json_str = EntityUtils.toString(request.getEntity());
                    if( json_str.equals("[]") ) {
                        return null;
                    }
					JSONObject study_data = new JSONObject(json_str);
                    return study_data;
				} catch (ParseException e) {
					e.printStackTrace();
				} catch (JSONException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			return null;
		}
    	
		@Override
		protected void onPostExecute(JSONObject result) {
			super.onPostExecute(result);
			
			loader.dismiss();
			
			if( result == null ) {
				AlertDialog.Builder builder = new AlertDialog.Builder(CameraStudy.this);
	            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
	                @Override
	                public void onClick(DialogInterface dialog, int which) {
	                	setResult(Activity.RESULT_CANCELED);
	                    finish();
	                }
	            });
	            builder.setTitle("Study information");
				builder.setMessage("This study is no longer available.");
                builder.show();
			} else {
				AlertDialog.Builder builder = new AlertDialog.Builder(CameraStudy.this);
	            builder.setPositiveButton("Sign up!", new DialogInterface.OnClickListener() {
	                @Override
	                public void onClick(DialogInterface dialog, int which) {
	                    Intent study_scan = new Intent();
	                    study_scan.putExtra("study_url", study_url);
	                    setResult(Activity.RESULT_OK, study_scan);
	                    finish();
	                }
	            });
	            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
	                @Override
	                public void onClick(DialogInterface dialog, int which) {
	                    setResult(Activity.RESULT_CANCELED);
	                    finish();
	                }
	            });
	            builder.setTitle("Study information");
	            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	            View study_ui = inflater.inflate(R.layout.study_info, null);
	            TextView study_name = (TextView) study_ui.findViewById(R.id.study_name);
	            TextView study_description = (TextView) study_ui.findViewById(R.id.study_description);
	            TextView study_pi = (TextView) study_ui.findViewById(R.id.study_pi);
	            
	            try {
					study_name.setText((result.getString("study_name").length()>0 ? result.getString("study_name"): "Not available"));
					study_description.setText((result.getString("study_description").length()>0?result.getString("study_description"):"Not available."));
					study_pi.setText("PI: " + result.getString("researcher_first") + " " + result.getString("researcher_last") + "\nContact: " + result.getString("researcher_contact"));
				} catch (JSONException e) {
					e.printStackTrace();
				}
	            builder.setView(study_ui);
	            builder.show();
			}
		}
    }
    
    /**
     * Class that generates a camera preview on a surface
     * @author denzil
     *
     */
    private class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
        private SurfaceHolder mHolder;
        private Camera mCamera;
        private PreviewCallback mPreview;
        private AutoFocusCallback mAutofocus;
        
        public CameraPreview(Context context, Camera camera, PreviewCallback previewCB, AutoFocusCallback autofocusCB) {
            super(context);
            mCamera = camera;
            mPreview = previewCB;
            mAutofocus = autofocusCB;
            
            mHolder = getHolder();
            mHolder.addCallback(this);
        }
        
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
            	if( mCamera == null ) mCamera = Camera.open();
            	mCamera.setPreviewDisplay(holder);
            } catch( IOException e ) {
                if( Aware.DEBUG ) Log.d(Aware.TAG,"Failed to set camera preview...");
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if( mHolder.getSurface() == null ) {
                return;
            }
            
            try{
                mCamera.stopPreview();
            } catch( Exception e ) {
                //ignore: tried to stop non-existent preview
            }
            
            try{
                mCamera.setDisplayOrientation(90);
                mCamera.setPreviewDisplay(holder);
                mCamera.setPreviewCallback(mPreview);
                mCamera.startPreview();
                mCamera.autoFocus(mAutofocus);
            } catch ( IOException e ) {
                if( Aware.DEBUG ) Log.d(Aware.TAG,"Failed to set camera preview...");
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            //handled by activity;
        }
    }
}
