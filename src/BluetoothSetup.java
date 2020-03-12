/********************************************************************
* BluetoothSetup.java - Sets up main activity. Finds paired and discoverable
*                    Bluetooth devices.
* 05/15/2013 Adam Berry
*
* Loosely based off of Google Chat Android example. 
* 2009 Author not listed.
*********************************************************************/

package com.example.nokeydo;
import android.os.Bundle;
import android.os.Message;
import android.app.Activity;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.AdapterView;
import android.util.Log;
import android.os.Handler;

public class MainActivity extends Activity {
    // Debugging
    private static final String TAG = "BluetoothApp";
    private static final boolean D = true;
    
    // Private members - Adapter, paired, and discovered!
    private BluetoothAdapter BTAdapter = BluetoothAdapter.getDefaultAdapter();
    private ArrayAdapter<String> PairedDevices;
    private ArrayAdapter<String> NewDevices;
    
    // Bluetooth connection & device name.
    private BluetoothConnection Connection = null;
    private String ConnectedDevice = null;
    
    // Incoming/Outgoing Character Buffer
    private ArrayAdapter<String> RxBuffer;
    private StringBuffer mOutStringBuffer;
    
    // Handler events sent from the messaging thread.
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    
    // Security key handling
    public static final int TRANCIEVE_LENGTH = 10;
    public static String SECURITY_KEY = "";
    
    // Handler
    Handler delayHandler;
    public static final String TOAST = "toast";
    public static String EXTRA_DEVICE_ADDRESS = "device_address";
    public static final String DEVICE_NAME = "device_name";
    
    // Lock/Unlock Buttons
    private ImageView lock_image;
    private ProgressBar spinner;
    
    // Lock State
    private static String lState = "UNLOCKED";
    private static final String LOCK_STATE_CH = "~";
    
    // Default Device - Auto-connect
    String DEVICE_FILE = "default_device";
    String default_device = "";
    public static final int MAX_DEVICE_LENGTH = 15;
    
    // Sync State
    private static String sState = "FALSE";
    String SECURITY_FILE = "security_key";
    int index = 0;
    
    // Current view
    String CURRENT_VIEW = "home";
    
    /*
     * onCreate - Instantiates the main activity. Gets default Bluetooth device.
     *
     * @param  Bundle saveInstanceState application state
     *
     * @return None
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Remove title bar
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Remove notification bar
        this.getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN, 
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        
        try {
            BluetoothGetDefault();
            ScanToast("Active Device: " + default_device, 2000, true, false);
        } catch (IOException e) {
            // No default device file
            e.printStackTrace();
        }
        
        // Set result to cancel if the user backs out.
        setResult(Activity.RESULT_CANCELED);
        
        // Set background color.
        View view = MainActivity.this.getWindow().getDecorView();
        view.setBackgroundColor(0xffffff);
        
        HomeScreenInit();
    }
    
    /*
     * HomeScreenInit - Sets button onclick events.
     *
     * @return None
     */
    protected void HomeScreenInit(void) {
        // Set view to this view
        setContentView(R.layout.activity_main);
        
        // Scan Bluetooth
        Button scan = (Button) findViewById(R.id.scan_button);
        scan.setOnClickListener(new OnClickListener() {
            public void onClick(View e) {
                BluetoothScan(false);
            }
        });
        
        // Auto-Connect
        Button connect = (Button) findViewById(R.id.connect_button);
        connect.setOnClickListener(new OnClickListener() {
            public void onClick(View e) {
                BluetoothScan(true);
            }
        });
    }
    
    /*
     * BluetoothScan - Scans for new Bluetooth devices and paired devices.
     *
     * @param Boolean autoConnect connects the active device
     *
     * @return None
     */
    protected void BluetoothScan(Boolean autoConnect) {
        Log.d(TAG, "Tag: Initializing Bluetooth");
        BluetoothInit();
        if (autoConnect == false) {
            Log.d(TAG, "Tag: Start Discovery");
            BluetoothStartDiscovery();
            
            // Switch view & toast
            setContentView(R.layout.device_list);
            CURRENT_VIEW = "device_list";
            
            // Initialize array adapters
            PairedDevices = new ArrayAdapter<String>(
                MainActivity.this, R.layout.device_name
            );
            NewDevices = new ArrayAdapter<String>(
                MainActivity.this, R.layout.device_name
            );
            
            // Paired devices - Set up array & item onclick
            ListView pairedListView = (ListView) findViewById(
                R.id.paired_devices
            );
            pairedListView.setAdapter(PairedDevices);
            pairedListView.setOnItemClickListener(mDeviceClickListener);
            
            // Paired devices - Populate paired list
            BluetoothPairList(PairedDevices);
            
            // New devices - Set up array & item onclick
            ListView newDevicesListView = (ListView) findViewById(
                R.id.new_devices
            );
            newDevicesListView.setAdapter(NewDevices);
            newDevicesListView.setOnItemClickListener(mDeviceClickListener);
            
            // Register for broadcasts when a device is discovered
            IntentFilter filter = new IntentFilter(
                BluetoothDevice.ACTION_FOUND
            );
            MainActivity.this.registerReceiver(mReceiver, filter);

            // Register for broadcasts when discovery has finished
            filter = new IntentFilter(
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED
            );
            MainActivity.this.registerReceiver(mReceiver, filter);
            
            // Rescan button!
            Button scanButton = (Button) findViewById(R.id.rescan_button);
            scanButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    ScanToast("Scanning Devices", 1000, true, true);
                    
                    // Invisible button
                    v.setVisibility(View.GONE);
                    
                    // Invisible list
                    ListView paired_list = (ListView) findViewById(
                        R.id.paired_devices
                    );
                    paired_list.setVisibility(View.GONE);
                    
                    // Change String Header
                    TextView device_header = (TextView) findViewById(
                        R.id.device_header
                    );
                    device_header.setText("New Devices");
                    
                    // Visible Button
                    Button paired_button = (Button) findViewById(
                        R.id.paired_button
                    );
                    paired_button.setVisibility(View.VISIBLE);
                    
                    // Visible List
                    ListView new_list = (ListView) findViewById(
                        R.id.new_devices
                    );
                    new_list.setVisibility(View.VISIBLE);
                }
            });
            
            Button pairedButton = (Button) findViewById(R.id.paired_button);
            pairedButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    v.setVisibility(View.GONE);
                    
                    // Invisible list
                    ListView new_list = (ListView) findViewById(
                        R.id.new_devices
                    );
                    new_list.setVisibility(View.GONE);
                    
                    // Change String Header
                    TextView device_header = (TextView) findViewById(
                        R.id.device_header
                    );
                    device_header.setText("Paired Devices");
                    
                    // Visible Button
                    Button new_button = (Button) findViewById(
                        R.id.rescan_button
                    );
                    new_button.setVisibility(View.VISIBLE);
                    
                    // Visible List
                    ListView paired_list = (ListView) findViewById(
                        R.id.paired_devices
                    );
                    paired_list.setVisibility(View.VISIBLE);
                }
            });
        }  else {
            // Paired devices - Populate paired list
            Set<BluetoothDevice> pairedDevices = BTAdapter.getBondedDevices();
            // If there are paired devices
            if (pairedDevices.size() > 0) {
                // Loop through paired devices
                for (BluetoothDevice device : pairedDevices) {
                    // Add the name and address to an array adapter
                    Log.d(TAG, "TAG: "+device.getName().toString().trim());
                    if (device.getName().equals(
                        default_device.toString().trim())
                    ) {
                        BluetoothDevice connect_device = BTAdapter.getRemoteDevice(
                            device.getAddress()
                        );
                        Log.d(TAG, "Tag: "+connect_device);
                            
                        setContentView(R.layout.connecting);
                        ScanToast(
                            "Connecting to "+device.getName(), 2000, true, false
                        );
                        CURRENT_VIEW = "connecting";
                        
                        Connection = new BluetoothConnection(
                            BTAdapter, device, mHandler
                        );
                        Connection.connect();
                    }
                }
            }
        }
    }
    
    /*
     * BluetoothInit - Makes sure user has bluetooth & turns it on.
     *
     * @return None
     */
    protected void BluetoothInit(void) {        
        if (BTAdapter == null) {
             ScanToast("You don't have Bluetooth");
             finish();
        } else {}
        
        // Enable Bluetooth
        if (!BTAdapter.isEnabled()) {
              Intent enableBtIntent = new Intent(
                  BluetoothAdapter.ACTION_REQUEST_ENABLE
              );
              startActivityForResult(enableBtIntent, 5);
        } else {}
    }
    
    /*
     * BluetoothGetDefault - Gets the default Bluetooth device.
     *
     * @return None
     */
    protected void BluetoothGetDefault(void) throws IOException {
        byte[] buffer = new byte[MAX_DEVICE_LENGTH];
        
        // Get default 
        FileInputStream fos = openFileInput(DEVICE_FILE);
        fos.read(buffer);
        fos.close();
        
        // Save default name
        default_device = new String(buffer);
    }
    
    /*
     * BluetoothSetDefault - Saves the last paired Bluetooth device.
     *
     * @return None
     */
    protected void BluetoothSetDefault(BluetoothDevice device) throws IOException {
        FileOutputStream fos = openFileOutput(
            DEVICE_FILE, Context.MODE_PRIVATE
        );
        fos.write(device.getName().getBytes());
        fos.close();
    }
    
    /*
     * BluetoothStartDiscovery - Starts discovery of new devices.
     *
     * @return None
     */
    protected void BluetoothStartDiscovery() {
        // See if it is discovering
        if (BTAdapter.isDiscovering()) {
              BTAdapter.cancelDiscovery();
        }
        
        // Start discovery
        BTAdapter.startDiscovery();
    }
    
    /*
     * BluetoothPairList - Fills ListView with paired devices.
     *
     * @param ArrayAdapter paired is the array of paired devices
     *
     * @return None
     */
    protected void BluetoothPairList(ArrayAdapter<String> paired) {
        Set<BluetoothDevice> pairedDevices = BTAdapter.getBondedDevices();
        // If there are paired devices
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                // Add device to list view
                paired.add(device.getName() + "\n" + device.getAddress());
            }
        }
    }
    
    /*
     * BroadcastReceiver - Fills ArrayAdapter with new devices.
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE
                );
                // If it's already paired, skip it.
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    NewDevices.add(
                        device.getName() + "\n" + device.getAddress()
                    );
                }
            // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                // Done searching
                setProgressBarIndeterminateVisibility(false);
            }
        }
    };
    
    /*
     * mDeviceClickListener - Set onclick listener for devices.
     */
    private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView av, View v, int arg2, long arg3) {
            // Cancel discovery because it's costly and we're about to connect
            BTAdapter.cancelDiscovery();
     
            
            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            
            // Connecting view
            setContentView(R.layout.connecting);
            ScanToast("Connecting to "+info.substring(0, 12), 2000, true, false);
            
            String address = info.substring(info.length() - 17);
            BluetoothDevice device = BTAdapter.getRemoteDevice(address);
            Log.d(TAG, "Tag: "+device);
            
            // Last connected device is set as default device
            try {
                BluetoothSetDefault(device);
            } catch (IOException e) {
                // Auto-generated catch block
                e.printStackTrace();
            }
            Connection = new BluetoothConnection(BTAdapter, device, mHandler);
            Connection.connect();
        }
    };
    
    /*
     * PrepLockActivity - Finds key, instantiates lock activity.
     *
     * @return None
     */
    private void PrepLockActivity(void) {
        setContentView(R.layout.lock_activity);
        CURRENT_VIEW = "lock_screen";
        
        // Get the security key if it is saved.
        byte[] buffer = new byte[MAX_DEVICE_LENGTH];
        
        // Get input security key
        FileInputStream fis = null;
        try {
            fis = openFileInput(SECURITY_FILE);
        } catch (FileNotFoundException e1) {
            ScanToast("Key file not found!", 2000, false, false);
        }
        try {
            fis.read(buffer);
        } catch (IOException e1) {
            ScanToast("Unable to read key!", 2000, false, false);
        }
        try {
            fis.close();
        } catch (IOException e1) {
            ScanToast("Unable to close", 2000, false, false);
        }
        
        // Save default name
        SECURITY_KEY = new String(buffer).toString().trim();
        
        // Initialize the array adapter for the conversation thread
        RxBuffer = new ArrayAdapter<String>(
            MainActivity.this, R.layout.device_name
        );
        
        // Lock Image
        lock_image = (ImageView) findViewById(R.id.lock_logo);
        lock_image.setVisibility(View.GONE);
        lock_image.setOnClickListener(new OnClickListener() {
            public void onClick(View e) {
                if (SECURITY_KEY == "") {
                    ScanToast("Sync Phone", 1000, false, false);
                } else if (lState == "UNLOCKED") {
                    String message = SECURITY_KEY.concat("_l");
                    SendMessage(message);
                    
                    // Delay for motor change
                    e.setClickable(false);
                    spinner = (ProgressBar) findViewById(R.id.spinner);
                    spinner.setVisibility(View.VISIBLE);
                    Runnable r = new Runnable() {
                        @Override
                        public void run() {
                            lock_image = (ImageView) findViewById(
                                R.id.lock_logo
                            );
                            lock_image.setClickable(true);
                            spinner.setVisibility(View.GONE);
                        }
                    };
                    Handler h = new Handler();
                    h.postDelayed(r, 1500);
                    
                } else if (lState == "LOCKED") {
                    String message = SECURITY_KEY.concat("_u");
                    SendMessage(message);
                    
                    // Delay for motor change
                    e.setClickable(false);
                    spinner = (ProgressBar) findViewById(R.id.spinner);
                    spinner.setVisibility(View.VISIBLE);
                    Runnable r = new Runnable() {
                        @Override
                        public void run() {
                            lock_image = (ImageView) findViewById(
                                R.id.lock_logo
                            );
                            lock_image.setClickable(true);
                            spinner.setVisibility(View.GONE);
                        }
                    };
                    Handler h = new Handler();
                    h.postDelayed(r, 1500);
                }
            }
        });
        
        mOutStringBuffer = new StringBuffer("");
    }
    
    /**
     * SendMessage - Sends a message via Bluetooth.
     *
     * @param message  A string of text to send.
     *
     * @return None
     */
    private void SendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (Connection.getState() != BluetoothConnection.STATE_CONNECTED) {
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            Connection.write(send);
        }
    }
    
    /**
     * mHandler - Handler to communicate with threads.
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                switch (msg.arg1) {
                case BluetoothConnection.STATE_CONNECTED:
                    RxBuffer.clear();
                    break;
                case BluetoothConnection.STATE_CONNECTING:
                    break;
                case BluetoothConnection.STATE_CONNECTION_FAILED:
                    ScanToast("Connection Failed", 2000, true, false);
                    HomeScreenInit();
                    CURRENT_VIEW = "home";
                    break;
                case BluetoothConnection.STATE_NONE:
                    break;
                }
                break;
            case MESSAGE_WRITE:
                byte[] writeBuf = (byte[]) msg.obj;
                // construct a string from the buffer
                break;
            case MESSAGE_READ:
                // Receiving charters
                byte[] readBuf = (byte[]) msg.obj;
                String readMessage = new String(readBuf, 0, msg.arg1);
                RxBuffer.add(readMessage);
                if (readMessage.charAt(0) == 's') {
                    SECURITY_KEY = "";
                    index = 0;
                    sState = "true";
                    ScanToast("Pairing...", 2000, false, false);
                } else if (sState == "true") {
                    // If buffer full, change state back.
                    SECURITY_KEY += readMessage.charAt(0);
                    index++;
                    
                    if (index == 8) {
                        // echo debug security
                        index = 0;
                        sState = "false";
                        
                        // Fill security buffer
                        FileOutputStream fos = null;
                        try {
                            fos = openFileOutput(
                                SECURITY_FILE, Context.MODE_PRIVATE
                            );
                        } catch (FileNotFoundException e1) {
                            //  Auto-generated catch block
                            e1.printStackTrace();
                        }
                        try {
                            fos.write(SECURITY_KEY.getBytes());
                            index++;
                        } catch (IOException e) {
                            //  Auto-generated catch block
                            e.printStackTrace();
                        }
                        try {
                            fos.close();
                        } catch (IOException e) {
                            //  Auto-generated catch block
                            e.printStackTrace();
                        } 
                    }
                } else if (readMessage.charAt(0) == 'l') {
                    if (readMessage.charAt(0) == 'l') {
                        ScanToast("Locking...", 2000, true, false);
                    }
                    lState = "LOCKED";
                    lock_image = (ImageView) findViewById(R.id.lock_logo);
                    lock_image.setBackgroundResource(R.drawable.locked);
                } else if (readMessage.charAt(0) == 'u') {
                    if (readMessage.charAt(0) == 'u') {
                        ScanToast("Unlocking...", 2000, true, false);
                    }
                    lState = "UNLOCKED";
                    lock_image = (ImageView) findViewById(R.id.lock_logo);
                    lock_image.setBackgroundResource(R.drawable.unlocked);
                }
                break;
            case MESSAGE_DEVICE_NAME:
                // Device Connected
                MainActivity.this.SendMessage(LOCK_STATE_CH);
                PrepLockActivity();
                spinner = (ProgressBar) findViewById(R.id.spinner);
                lock_image = (ImageView) findViewById(R.id.lock_logo);
                spinner.setVisibility(View.GONE);
                lock_image.setVisibility(View.VISIBLE);
                ConnectedDevice = msg.getData().getString(DEVICE_NAME);
                ScanToast("Connected to " + ConnectedDevice, 2000, true, false);
                break;
            case MESSAGE_TOAST:
                // Toast from a thread
                Toast.makeText(
                    getApplicationContext(), msg.getData().getString(TOAST),
                    Toast.LENGTH_SHORT
                ).show();
                break;
            }
        }
    };
    
    /*
     * ScanToast - Sends a toast to the UI
     *
     * @param String  toast_message - Message to be sent
     * @param Int     time          - Time to leave toast on screen
     * @param Boolean top           - Position at top
     * @param Boolean right         - Position to right
     *
     * @return None
     */
    protected void ScanToast(
        String toast_message, int time, Boolean top, Boolean right
    ) {
        Context context = getApplicationContext();
        CharSequence text = toast_message;
        int duration = Toast.LENGTH_SHORT;
        
        final Toast toast = Toast.makeText(context, text, duration);
        if (top && right) {
            toast.setGravity(Gravity.TOP|Gravity.RIGHT, 0, 0);
        } else if (top && !right) {
            toast.setGravity(Gravity.TOP, 0, 0);
        } else if (!top && right) {
            toast.setGravity(Gravity.RIGHT, 0, 0);
        } else {}
        toast.show();
        
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
           @Override
           public void run() {
               toast.cancel(); 
           }
        }, time);
        
    }
    
    /*
     * onDestroy - Take action when application quits
     *
     * @return None
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (Connection != null) {
            try {
                Connection.StopThreads();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        // Make sure we're not doing discovery anymore
        if (BTAdapter != null) {
            BTAdapter.cancelDiscovery();
        }

        // Unregister broadcast listeners
        this.unregisterReceiver(mReceiver);
    }
    
    /*
     * onBackPressed - Take action when back button is pressed
     *
     * @return None
     */
    @Override
    public void onBackPressed(void) {
        // if home = quit else go home
        if (CURRENT_VIEW == "home") {
            finish();
        } else {
            HomeScreenInit();
            CURRENT_VIEW = "home";
        }
        onDestroy();
    }
    
    /*
     * onCreateOptionsMenu - Add settings menu items.
     *
     * @return None
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
}