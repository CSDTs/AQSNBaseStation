package com.logrit.spaceshipspotter;

import java.util.*;

import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.bluetooth.*;
import android.os.Bundle;
import android.view.*;
import android.content.*;
import android.app.*;
import android.widget.*;

import android.app.ListActivity;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.*;
import android.widget.Toast;


public class BlueToothSelectionActivity extends ActionBarActivity {

	ArrayAdapter<String> adapter = null;
	List<String> BTDeviceList = new ArrayList<String>();
	BluetoothAdapter mBluetoothAdapter;
	public int SelectedItem = -1;
	private Set<BluetoothDevice>pairedDevices;
	
	;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_blue_tooth_selection);
		final ListView listview = (ListView) findViewById(R.id.BTDevices);
	    ActionBar actionBar = getSupportActionBar();
	    actionBar.setDisplayHomeAsUpEnabled(true);
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		 
		if (!mBluetoothAdapter.isEnabled()) 
		{
			Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(turnOn, 0);      
		}
		
		pairedDevices = mBluetoothAdapter.getBondedDevices();
		
		for(BluetoothDevice bt : pairedDevices)
		   BTDeviceList.add(bt.getName());
		
		adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, BTDeviceList);
		
		listview.setAdapter(adapter);
		listview.setOnItemClickListener(new OnItemClickListener() {

		      @Override
		      public void onItemClick(AdapterView<?> parent, View view,
		          int position, long id) {
		        SelectedItem = position;

		        // start the CAB using the ActionMode.Callback defined above
		        view.setSelected(true);
		        BluetoothDevice BT = (BluetoothDevice) pairedDevices.toArray()[position];
				Intent Intent = new Intent(getApplicationContext(), MainActivity.class);
				Intent.putExtra("BluetoothDevice", BT); //Optional parameters
				startActivity(Intent);
		      }
		    });

		}
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.blue_tooth_selection, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
//		int id = item.getItemId();
//		if (id == R.id.action_settings) {
//			return true;
//		}
		return super.onOptionsItemSelected(item);
	}
}
