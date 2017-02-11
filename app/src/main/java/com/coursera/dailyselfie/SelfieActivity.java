package com.coursera.dailyselfie;

import java.io.File;
import java.io.IOException;

import com.coursera.dailyselfie.provider.SelfieContract;

import android.app.AlarmManager;
import android.app.ListActivity;
import android.app.LoaderManager.LoaderCallbacks;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

public class SelfieActivity extends ListActivity implements LoaderCallbacks<Cursor>{
	
	private static final String TAG = "DailySelfie";
	
	private String mSelfieBitmapPath;
	
	private static final String ALARM_KEY = "alarms";
	private static final String SELFIE_KEY = "selfiePath";
	
	private static final int REQUEST_TAKE_PHOTO = 1;
	
	private static final long INITIAL_ALARM_DELAY = 2 * 60 * 1000L;
	private static final long REPEAT_ALARM_DELAY = 2 * 60 * 1000L;
	
	private PendingIntent mNotificationReceiverPendingIntent;
	
	private SharedPreferences mSharedPreferences;
	private SelfieAdapter mAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null) {
			mSelfieBitmapPath = savedInstanceState.getString(SELFIE_KEY);
			Log.d(TAG,"restored selfiePhotoPath");
		}
		
		mAdapter = new SelfieAdapter(this);
		
		//View Initialization
		getListView().setAdapter(mAdapter);
		getLoaderManager().initLoader(0, null, this);
		mSharedPreferences = getSharedPreferences("selfie", Context.MODE_PRIVATE);
		
		setAlarm(null,false);
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		Log.d(TAG,"click on item at position "+position);
		SelfieRecord selfie = (SelfieRecord) mAdapter.getItem(position);
		Log.d(TAG, "fetched item "+ selfie.getName());
		Intent intent = new Intent(this,FullScreenActivity.class);
		intent.putExtra(FullScreenActivity.EXTRA_NAME,selfie.getName());
		intent.putExtra(FullScreenActivity.EXTRA_PATH,selfie.getPath());
		Log.i(TAG,"opening fullscreen activity");
		startActivity(intent);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		
		MenuItem item = menu.findItem(R.id.action_alarm);
		//Setting the original enable/disable value for alarms
		setAlarm(item,false);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_picture) {
			Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		    // Ensure that there's a camera activity to handle the intent
		    if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
		        // Create the File where the photo should go
		        File photoFile = null;
		        try {
		        	Log.i(TAG,"creating temp file");
		            photoFile = BitmapUtil.createImageFile();
		            mSelfieBitmapPath = photoFile.getAbsolutePath();
		            Log.d(TAG,"temp file at: "+ mSelfieBitmapPath);
		        } catch (IOException ex) {
		            // Error occurred while creating the File
		           	Log.w(TAG,"could not create image file",ex);
		        }
		        // Continue only if the File was successfully created
		        if (photoFile != null) {
		        	Log.i(TAG,"starting camera intent");
		            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
		                    Uri.fromFile(photoFile));
		            startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
		        }
		    }
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	protected void setAlarm(MenuItem item, boolean toggle){
		//Setting the alarm
		if (mNotificationReceiverPendingIntent == null) {
			Log.d(TAG,"initiating alarm operation");
			mNotificationReceiverPendingIntent = PendingIntent.getBroadcast(
				getApplicationContext(), 
				0, 
				new Intent(getApplicationContext(),AlarmNotificationReceiver.class), 
				0);
		}
		
		boolean alarmEnabled = mSharedPreferences.getBoolean(ALARM_KEY, true);
		if (toggle) {
			Log.d(TAG,"requesting alarm toggle");
			alarmEnabled = !alarmEnabled;
			mSharedPreferences.edit().putBoolean(ALARM_KEY, alarmEnabled).commit();
		}
		
		AlarmManager alarm = (AlarmManager) getSystemService(Service.ALARM_SERVICE);
		if (alarmEnabled) {
			Log.i(TAG,"programming alarm");
			alarm.setRepeating(
					AlarmManager.ELAPSED_REALTIME_WAKEUP, 
					SystemClock.elapsedRealtime()+INITIAL_ALARM_DELAY, 
					REPEAT_ALARM_DELAY, mNotificationReceiverPendingIntent);
		} else {
			Log.i(TAG,"alarm disabled, canceling");
			alarm.cancel(mNotificationReceiverPendingIntent);
		}
		
		if (item != null) {
			if (alarmEnabled)
				item.setTitle(R.string.action_disable_alarm);
			else
				item.setTitle(R.string.action_enable_alarm);
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (REQUEST_TAKE_PHOTO == requestCode) {
			if (resultCode == RESULT_CANCELED){
				Log.i(TAG,"user canceled, deleting file...");
				new File(mSelfieBitmapPath).delete();
			}
			if (resultCode == RESULT_OK) {
				Log.i(TAG,"processing selfie");
				SelfieRecord selfie = new SelfieRecord();
				selfie.setName(new File(mSelfieBitmapPath).getName());
				selfie.setPath(mSelfieBitmapPath);
				
				Log.i(TAG,"creating thumb bitmap");
				Bitmap fullSized = BitmapUtil.getBitmapFromFile(mSelfieBitmapPath);
				Float aspectRatio = ((float)fullSized.getHeight())/(float)fullSized.getWidth();
				Bitmap thumb = Bitmap.createScaledBitmap(
						fullSized,
						120, 
						(int)(120*aspectRatio), 
						false);
				String thumbPath = BitmapUtil.getThumbPath(mSelfieBitmapPath);
		        selfie.setThumbPath(thumbPath);
		        BitmapUtil.storeBitmapToFile(thumb, thumbPath);
		        
		        Log.i(TAG,"recycling resources");
		        fullSized.recycle();
		        thumb.recycle();
				
		        mSelfieBitmapPath = null;
				
				Log.i(TAG,"adding selfie to adapter");
				mAdapter.addSelfie(selfie);
			}
		}
		
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Log.d(TAG,"configuration is changing, saving instance state");
		outState.putString(SELFIE_KEY, mSelfieBitmapPath);
	};

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		// TODO Auto-generated method stub
		CursorLoader loader = new CursorLoader(this, SelfieContract.SELFIE_URI, null, null,null,null);
		return loader;
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		// TODO Auto-generated method stub
		mAdapter.swapCursor(data);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		// TODO Auto-generated method stub
		mAdapter.swapCursor(null);
	}
}