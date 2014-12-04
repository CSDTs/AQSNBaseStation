package com.logrit.spaceshipspotter;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.nio.ByteBuffer;

import static us.monoid.web.Resty.*;
import us.monoid.json.JSONObject;
import us.monoid.web.Resty;
import android.support.v7.app.ActionBarActivity;
import android.support.v4.app.DialogFragment;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.bluetooth.*;
import android.widget.*;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;

public class MainActivity extends ActionBarActivity implements
		SensorEventListener, GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener {
	

	private static final int lengthOfDataToSend = 100;

	//MessageCodes
	private static final char RequestData = 'R';
	private static final char BaseStationRequest = 'B';
	private static final char RequestPower = 'P';
	private static final char ReplyPower = 'L';
	private static final char ReplyData = 'D';
	private static final char SendingForwardingID = 'F';
	private static final char SendingReturnID = 'I';
	private static final char ResetNetwork = 'N';
	private static final char TestingConnection = 'T';
	private static final char CompletedBaseStationRequest = 'C';
	private static final char SynchronizeNeighbors = 'S';
	private static final char IncomingTimeMessage = '1';
	private static final char RequestTimeMessage = '2';
	private static final char TimeDifferenceOutgoingMessage = '3';
	private static final char DoneTimeSync = '4';
	private static final char FindAdjacentNodes = 'A';
	private static final char GenerateRoutingTable = 'G';
	private static final char IncomingListOfNodes = 'O';
	private static final char RequestCloseConnection = 'K'; //Because of android and it's stupid tunnels
	private static final char RequestFireYourLasers = 'E'; //Fires The Lasers
	private static final char FindYourMACAddress = 'M'; //remove after testing
	private static final char AssignYourName = 'Q'; //remove after testing
	private static final char FindName = '9'; //remove after testing
	
	private byte power = 0;
	private String ReturnID = "";
	private long timeDifference = 0;
	private boolean isTimeSynchronizationDone = false;
	private boolean isTimeSynchronizing = false;
	private String MACAddress = "";
	private long networkTime = 0;
	
	private TextView myText;
	private SensorManager mSensorManager;
	private Sensor mLight;
	private List<Sensor> sensors;
	private LocationClient mLocationClient;
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothSocket bluetoothSocket;
	private OutputStream outputStream;
	private InputStream inputStream;
	private BluetoothDevice device;
	boolean stopWorker;
	private String message;
	int readBufferPosition;
	byte[] readBuffer;
	Thread workerThread;
	List<SyncPoint> syncPoints;
	public static final String BASE_URL = "http://spaceshipspotter.logrit.com/api";
	boolean recording = false;
	
	
	char RequestState = 'N';
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		LinearLayout layout = (LinearLayout) findViewById(R.id.container);

		EditText ConnectedDevice = (EditText)findViewById(R.id.ConnectedDevice);
		
		Intent i = getIntent();
		try
		{
			device = (BluetoothDevice)i.getExtras().getParcelable("BluetoothDevice");
			ConnectedDevice.setText(device.getName());
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		}
		catch(Exception e)
		{}
		finally
		{}
	}

	public void log(String s) {
		myText.setText(s + "\n" + myText.getText());
		// If myText is > 100mb, just truncate it
		if (myText.getText().length() > (8 * 1024 * 1024)) {
			myText.setText(myText.getText().subSequence(0, 8 * 1024 * 1024));
		}

		Log.i("arduino comm", s);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
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
	@SuppressLint("NewApi") public void ConnectToSensorNode() throws Exception
	{
		device.createBond();
		UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
		bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid);
		if(!bluetoothSocket.isConnected()) bluetoothSocket.connect();
		outputStream = bluetoothSocket.getOutputStream();
		inputStream = bluetoothSocket.getInputStream();

			if(!stopWorker)BeginListenForData();
	
	}
	@SuppressLint("NewApi")public void FireYourLasers(View view) 
	{
		try
		{
			if(bluetoothSocket==null || !bluetoothSocket.isConnected())ConnectToSensorNode();
			SendFireYourLasers();
		}
		catch(Exception e)
		{
			ShowError("NOT THE LASERS!!!: " + e.getMessage());
		}
	}
	@SuppressLint("NewApi")public void DownloadSensorData(View view) 
	{
		try
		{
			if(bluetoothSocket==null || !bluetoothSocket.isConnected())ConnectToSensorNode();
			QueryNetwork();
		}
		catch(Exception e)
		{
			ShowError("Error Getting Data: " + e.getMessage());
		}
	}
	@SuppressLint("NewApi")public void GenerateRoutingTable(View view) 
	{
		try
		{
			if(bluetoothSocket==null || !bluetoothSocket.isConnected())ConnectToSensorNode();
			QueryNetwork();
		}
		catch(Exception e)
		{
			ShowError("Error Getting Data: " + e.getMessage());
		}
	}
	
	@SuppressLint("NewApi") public void findSensorNetwork(View view)
	{
		try
		{
			closeBT();
		}
		catch(Exception e)
		{
		}
		Intent Intent = new Intent(getApplicationContext(), BlueToothSelectionActivity.class);
		startActivity(Intent);
	}
	
	@SuppressLint("NewApi") public void ResetNetwork(View view)
	{
		try
		{
			if(bluetoothSocket==null || !bluetoothSocket.isConnected())
			
				ConnectToSensorNode();
			ResetNetwork();
		}
		catch(Exception e)
		{
			ShowError("Error Reseting Network: " + e.getMessage());
		}
	}
	
	@SuppressLint("NewApi") public void SynchronizeTime(View view)
	{
		try
		{
			if(bluetoothSocket==null || !bluetoothSocket.isConnected())ConnectToSensorNode();
			SynchronizeNetwork();
		}
		catch(Exception e)
		{
			ShowError("Error Synchronizing: " + e.getMessage());
		}
	}
	
	@SuppressLint("NewApi") public void CheckPower(View view)
	{
		try
		{
			if(bluetoothSocket==null || !bluetoothSocket.isConnected())ConnectToSensorNode();
			throw new Exception("feature not completed") ;
		}
		catch(Exception e)
		{
			ShowError("You can't check your power yet");
		}
	}
	
	void BeginListenForData()
	{
		final Handler handler = new Handler(); 
	    final byte delimiter = 10; //This is the ASCII code for a newline character
	    message = "";
	    stopWorker = false;
	    readBufferPosition = 0;
	    readBuffer = new byte[1024];
	    workerThread = new Thread(new Runnable()
	    {
	        public void run()
	        {

	    		String fileName = "example2.txt";
	    		String myDirectory = "Documents";
	    		String externalStorage = Environment.getExternalStorageDirectory().getAbsolutePath();
	    		File outputFile = new File(externalStorage + File.separator + myDirectory + File.separator + fileName);
	    		FileOutputStream outputStream = null;
	    		OutputStreamWriter outputWriter = null;
	    		int number = 0;
	    		try
	    		{
				outputStream = new FileOutputStream(outputFile);
				outputWriter = new OutputStreamWriter(outputStream);
	    		}
	    		catch(Exception e)
	    		{stopWorker =true;}
	    		try
	    		{
		           while(!Thread.currentThread().isInterrupted() && !stopWorker)
		           {
	                    int bytesAvailable = inputStream.available();                        
	                    if(bytesAvailable > 0)
	                    {
	                        byte[] packetBytes = new byte[bytesAvailable];
	                        inputStream.read(packetBytes);
	                        for(int i=0;i<bytesAvailable;i++)
	                        {
		                            readBuffer[readBufferPosition++] = packetBytes[i];
                            }
	                       	byte[] encodedBytes = new byte[readBufferPosition];
	                        System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
	                        final byte[] data = encodedBytes;
	                        readBufferPosition = 0;
	        				for(int j = 0; j<data.length; j++)
	        				{
	        					if((char)data[j]=='\n')//end of message
	        					{
	        						ReceiveMessage(message);
	        						message = "";
	        					}
	        					else//create message
	        					{
	        						message += (char)data[j];
	        					}
	        				}
                        }
                    }
					outputWriter.close();
					outputStream.close();
	    		}
	    		catch(Exception e)
	    		{}
		           }
	    });

	    workerThread.start();
	}
	/* Checks if external storage is available for read and write */
	public boolean isExternalStorageWritable() {
	    String state = Environment.getExternalStorageState();
	    if (Environment.MEDIA_MOUNTED.equals(state)) {
	        return true;
	    }
	    return false;
	}
	public void ReceiveMessage(String message)
	{
		char reply = ' ';
		for(int i = 0; i< message.length(); i++)
		{
			reply = message.charAt(i);
			try
			{
				switch (reply)
				{
					case RequestData:
						//cannot forward through base station atm
						break;
					case BaseStationRequest:
						outputStream.write(CompletedBaseStationRequest); //this should never happen... but if it ever did...
						break;
					case RequestPower:
						//cannot forward through base station atm
						break;
					case ReplyPower:
						power = (byte)message.charAt(i+1);
						i++;
						break;
					case ReplyData:
						if(message.length() >= i+lengthOfDataToSend)
						{
							message.substring(i, i+lengthOfDataToSend);
						}
						i+= lengthOfDataToSend;
						break;
					case SendingForwardingID:
						//cannot forward through the base station
						i+=3;
						break;
					case SendingReturnID:
						ReturnID = message.substring(i, i+12);
						i+=12;
						break;
					case ResetNetwork:
						//there is nothing to reset
						break;
					case TestingConnection:
						//there is nothing to test
						break;
					case CompletedBaseStationRequest:
						CloseCurrentFile();
						CreateNewFile();
						break;
					case SynchronizeNeighbors:
						//you cannot synchronize to yourself
						break;
					case IncomingTimeMessage:
						timeDifference = System.currentTimeMillis();
						networkTime = ByteBuffer.wrap(message.substring(i,i+4).getBytes()).getLong();
						timeDifference -= networkTime;
			            outputStream.write(TimeDifferenceOutgoingMessage);
			            outputStream.write(ByteBuffer.allocate(8).putLong(timeDifference).array());
			            EndMessage();
			            if(isTimeSynchronizing)
			            {
			            	outputStream.write(SynchronizeNeighbors);
			            	EndMessage();
		            	}
			            i+=4;
						break;
					case RequestTimeMessage:
						//you should never need to request the time of the base station (provided on synchronization)
						break;
					case TimeDifferenceOutgoingMessage:
						//you do not need to do anything with outgoing time differences, because you do not synchronize
						break;
					case DoneTimeSync:
						isTimeSynchronizationDone = true;
						isTimeSynchronizing = false;
						break;
					case FindAdjacentNodes:
						//only the user can demand this
						break;
					case GenerateRoutingTable:
						//base station needs no routing table
						break;
					case FindYourMACAddress:
						//base station starts off knowing it's mac address
						break;
					case AssignYourName:
						//base station name can be whatever
						break;
					case FindName:
						//base station starts off knowing it's name
						break;
					case RequestCloseConnection: // because android won't let the server close it's connection
						closeBT();
						break;
					case IncomingListOfNodes: //the base station doesn't forward requests
						break;
					case RequestFireYourLasers: //fires the lasers
						break;
					}
				}
			catch(Exception e)  // to allow sending of data via outputStream (can technically throw exception)
			{ }
		}
	}
	public void SynchronizeNetwork() throws Exception
	{
		isTimeSynchronizing = true;
        SendRelayMessageHeader((byte)0);
        outputStream.write(IncomingTimeMessage);
        outputStream.write(ByteBuffer.allocate(8).putLong(System.currentTimeMillis()).array());
        EndMessage();
		outputStream.write(RequestTimeMessage);
		EndMessage();
	}
	public void ResetNetwork() throws Exception
	{
        SendRelayMessageHeader((byte)0);
		outputStream.write(ResetNetwork);
		EndMessage();
	}
	public void GetNetworkTime() throws Exception
	{
		outputStream.write(RequestTimeMessage);
		EndMessage();
	}
	public void QueryNetwork() throws Exception
	{
        SendRelayMessageHeader((byte)0);
		outputStream.write(BaseStationRequest);
		EndMessage();
	}
	void SendRelayMessageHeader(byte RelayTo) throws Exception
	{
		outputStream.write(SendingReturnID);
		outputStream.write(MACAddress.getBytes());
		outputStream.write(SendingForwardingID);
		outputStream.write(RelayTo);
		EndMessage();
	}
	void SendFireYourLasers() throws Exception
	{
		outputStream.write(RequestFireYourLasers);
		EndMessage();
	}
	public void EndMessage() throws Exception
	{
	   outputStream.write('.');
	   outputStream.write('\n');
	}
	public void CloseCurrentFile()
	{
		
	}
	public void CreateNewFile()
	{
		
	}
	public File getAlbumStorageDir(String albumName) {
	    // Get the directory for the user's public pictures directory. 
	    File file = new File(Environment.getExternalStoragePublicDirectory(
	            Environment.DIRECTORY_PICTURES), albumName);
	    return file;
	}
	@SuppressLint("NewApi") public void WriteRecords(byte[] data)
	{
		try
		{
		}
		catch(Exception e)
		{
			ShowError(e.getMessage());
		}
		
	}
	public int toInt(byte[] bytes, int offset) {
		  int ret = 0;
		  for (int i=0; i<4 && i+offset<bytes.length; i++) {
		    ret <<= 8;
		    ret |= (int)bytes[i] & 0xFF;
		  }
		  return ret;
		}
	public void ShowError(String error)
	{

		Dialog dialog = new Dialog(this);
		TextView txt = new TextView(this);
		txt.setText(error);
		dialog.setContentView(txt);
		dialog.setTitle("Error");
		dialog.show();
	}
	void RequestData() throws Exception
	{
	    outputStream.write(RequestData);
	    RequestState = RequestData;
	    EndMessage();
	}
	void RequestPower() throws Exception
	{
	    outputStream.write(RequestPower);
	    EndMessage();
	    RequestState = RequestPower;
	}
	void RequestNewRoutingTable() throws Exception
	{
	    outputStream.write(GenerateRoutingTable);
	    EndMessage();
	}
	
	void closeBT() throws IOException
	{
	    stopWorker = true;
	    outputStream.close();
	    inputStream.close();
	    bluetoothSocket.close();
	}

	public void syncServer(View view) {
		/*log("Synchronizing with server");
		
		log("Stopping recording to sync");
		
		Thread t = new Thread() {
			@Override
			public void run() {
				Resty r = new Resty();
				synchronized (syncPoints) {
					for(SyncPoint sync : syncPoints) {
						try {
							// POST /reports
							// Create the JSON object
							JSONObject json;
							json = new JSONObject();
							//json.put("lat", sync.lat);
							//json.put("lon", sync.lon);
							//json.put("timestamp", (new Timestamp(sync.timestamp)).toString());
							Integer report_id = (Integer)r.json(BASE_URL+"/reports/", content(json)).get("id");
							// POST /readings
							for(Reading reading : sync.r) {
								json = new JSONObject();
								json.put("sensor", reading.sensor);
								json.put("type", reading.type);
								json.put("accuracy", reading.accuracy);
								json.put("timestamp", (new Timestamp(reading.timestamp)).toString());
								json.put("report", report_id);
								
								Integer reading_id = (Integer)r.json(BASE_URL+"/readings/", content(json)).get("id");

								// POST /values
								for(float value : reading.values) {
									json = new JSONObject();
									json.put("reading", reading_id);
									json.put("value", value);
									r.json(BASE_URL+"/values/", content(json));
								}
							}
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			}
		};
		
		t.start();
		try {
			t.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
	}
	/**
	 * Stuff from the location services tutorial Lots of copy-pasta
	 */
	private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

	// Define a DialogFragment that displays the error dialog
	public static class ErrorDialogFragment extends DialogFragment {
		// Global field to contain the error dialog
		private Dialog mDialog;

		// Default constructor. Sets the dialog field to null
		public ErrorDialogFragment() {
			super();
			mDialog = null;
		}

		// Set the dialog to display
		public void setDialog(Dialog dialog) {
			mDialog = dialog;
		}

		// Return a Dialog to the DialogFragment.
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			return mDialog;
		}
	}

	/*
	 * Handle results returned to the FragmentActivity by Google Play services
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// Decide what to do based on the original request code
		switch (requestCode) {
		case CONNECTION_FAILURE_RESOLUTION_REQUEST:
			/*
			 * If the result code is Activity.RESULT_OK, try to connect again
			 */
			switch (resultCode) {
			case Activity.RESULT_OK:
				/*
				 * Try the request again
				 */
				break;
			}
		}
	}

	private boolean servicesConnected() {
		// Check that Google Play services is available
		int resultCode = GooglePlayServicesUtil
				.isGooglePlayServicesAvailable(this);
		// If Google Play services is available
		if (ConnectionResult.SUCCESS == resultCode) {
			// In debug mode, log the status
			Log.d("Location Updates", "Google Play services is available.");
			// Continue
			return true;
			// Google Play services was not available for some reason.
			// resultCode holds the error code.
		} else {
			// Get the error dialog from Google Play services
			Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(
					resultCode, this, CONNECTION_FAILURE_RESOLUTION_REQUEST);

			// If Google Play services can provide an error dialog
			if (errorDialog != null) {
				// Create a new DialogFragment for the error dialog
				ErrorDialogFragment errorFragment = new ErrorDialogFragment();
				// Set the dialog in the DialogFragment
				errorFragment.setDialog(errorDialog);
				// Show the error dialog in the DialogFragment
				errorFragment.show(getSupportFragmentManager(),
						"Location Updates");
			}
		}
		return false;
	}

	@Override
	public void onConnectionFailed(ConnectionResult arg0) {
		log(":( I have no idea");
	}

	@Override
	public void onConnected(Bundle arg0) {
		log("GPS Connected");
		
	}

	@Override
	public void onDisconnected() {
		log("GPS Disconnected");
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
		
	}
}
