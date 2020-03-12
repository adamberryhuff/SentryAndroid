/********************************************************************
* BluetoothConnection.java - Opens a Bluetooth Socket and manages
*                         Bluetooth Connection threads in Java
* 05/15/2013 Adam Berry
*
* Loosely based off of Google Chat Android example. 
* 2009 Author not listed.
*********************************************************************/

// Included Libraries
package com.example.nokeydo;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

/*
 * Class BluetoothConnection - Instantiates and manages Bluetooth Connection threads.
 */
public class BluetoothConnection {
    // Bluetooth specifics
    private final BluetoothAdapter BTAdapter;
    private final BluetoothDevice BTClient;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    
    // Communicate back to UI
    private final Handler BTHandler;
    
    // Connection Threads
    private ConnectThread BTConnectThread;
    private ConnectedThread BTConnectedThread;
    
    // State Machine
    private int state;
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_CONNECTION_FAILED = 1;
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    
    // Socket is the active connection.
    private BluetoothSocket mmSocket;
    
    // Debug
    private static final String TAG = "BluetoothConnection";

    /**
     * Constructor. Prepares a new BluetoothChat session.
     *
     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     */
    public BluetoothConnection(BluetoothAdapter adapter, BluetoothDevice device, Handler handler) {
        // Client module, receiver, context.
        BTAdapter = adapter;
        BTClient = device;
        BTHandler = handler;
        state = STATE_NONE;
        
        BTHandler.sendEmptyMessage(0);
    }
    
    /**
     * getState - Gets the current Bluetooth state.
     *
     * @return Current state
     */
    public synchronized int getState() {
        return state;
    }
    
    /**
     * getState - Clears the Tx & Rx threads.
     *
     * @return None
     */
    public synchronized void ClearThreads() {
        
        // Cancel connection thread
        if (BTConnectThread != null) {BTConnectThread.cancel(); BTConnectThread = null;}

        // Cancel connected thread
        if (BTConnectedThread != null) {BTConnectedThread.cancel(); BTConnectedThread = null;}

    }
    
    /**
     * StopThreads - Cancels teh threads. Closes BT Socket.
     *
     * @return None
     */
    public synchronized void StopThreads() throws IOException {

        if (BTConnectThread != null) {
            BTConnectThread.cancel();
            BTConnectThread = null;
            mmSocket.close();
        }

        if (BTConnectedThread != null) {
            BTConnectedThread.cancel();
            BTConnectedThread = null;
            mmSocket.close();
        }

        setState(STATE_NONE);
    }
    
    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (state != STATE_CONNECTED) return;
            r = BTConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }
    
    /**
     * connect - Starts the connection thread.
     *
     * @return None
     */
    public synchronized void connect() {
        ClearThreads();
        
        // Create Connection Thread
        BTConnectThread = new ConnectThread();
        BTConnectThread.start();
        setState(STATE_CONNECTING);
    }
    
    /**
     * ConnectThread - Thread to open a new socket.
     *
     * @return None
     */
    private class ConnectThread extends Thread {
        
        /**
         * ConnectThread - Start the thread. Create socket from UUID.
         *
         * @return None
         */
        public ConnectThread() {
            BluetoothSocket tmp = null;
            
            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                tmp = BTClient.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.d(TAG, "Tag: Socket Failure");
                setState(STATE_CONNECTION_FAILED);
            }
            Log.d(TAG, "Tag: Socket Created");
            
            mmSocket = tmp;
        }
    
        /**
         * run - Turns off discovery and connects socket.
         *
         * @return None
         */
        public void run() {
            
            // Always cancel discovery because it will slow down a connection
            BTAdapter.cancelDiscovery();
            
            try {
                // Try to connect
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                Log.d(TAG, "Tag: Couldn't Connect");
                setState(STATE_CONNECTION_FAILED);
                try {
                    mmSocket.close();
                    Log.d(TAG, "Tag: Socket Closed");
                } catch (IOException closeException) {
                    Log.d(TAG, "Tag: Socket Close Error");
                }
                return;
            }
            
            Log.d(TAG, "Tag: Bluetooth Connected");
            
            // Reset the ConnectThread because we're done
            synchronized (BluetoothConnection.this) {
                BTConnectThread = null;
            }
            
            
            // Start the connected thread
            connected(mmSocket);
        }
    
        /**
         * cancel - Close socket on cancel.
         *
         * @return None
         */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {}
        }
    }
    
    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket) {  
        Log.d(TAG, "Tag: Initializing Protocol");
        
        // Cancel the thread that completed the connection
        if (BTConnectThread != null) {BTConnectThread.cancel(); BTConnectThread = null;}

        // Cancel any thread currently running a connection
        if (BTConnectedThread != null) {BTConnectedThread.cancel(); BTConnectedThread = null;}

        // Start the thread to manage the connection and perform transmissions
        BTConnectedThread = new ConnectedThread(socket);
        BTConnectedThread.start();
        
        state = STATE_CONNECTED;
        
        Log.d(TAG, "Tag: Sending Test");
        // Send the name of the connected device back to the UI Activity
        Message msg = BTHandler.obtainMessage(MainActivity.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.DEVICE_NAME, BTClient.getName());
        msg.setData(bundle);
        BTHandler.sendMessage(msg);
        
        Log.d(TAG, "Tag: Test Sent");
        
        setState(STATE_CONNECTED);
    }
    
    /**
     * ConnectedThread - Manages an active connection. Passes Tx and Rx events to handler.
     *
     * @return None
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        
        /**
         * ConnectedThread - Instantiates in and out stream.
         *
         * @param BluetoothSocket socket is the open socket connection.
         *
         * @return None
         */
        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }
        
        /**
         * run - Reads input buffer.
         *
         * @return None
         */
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    Log.d(TAG, "Tag: Incoming - "+(char)buffer[0]);
                    
                    
                    // Send the obtained bytes to the UI Activity
                    BTHandler.obtainMessage(MainActivity.MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {}
            }
        }

        /**
         * write - Write to the connected OutStream.
         *
         * @param buffer  The bytes to write
         *
         * @return None
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);

                // Share the sent message back to the UI Activity
                BTHandler.obtainMessage(MainActivity.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
            } catch (IOException e) {}
        }
        
        /**
         * cancel - Cancels the thread. Closes the open socket.
         *
         * @return None
         */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
    
    /**
     * Set the current state of the chat connection
     *
     * @param state  An integer defining the current connection state
     *
     * @return None
     */
    private synchronized void setState(int new_state) {
        state = new_state;

        // Give the new state to the Handler so the UI Activity can update
        BTHandler.obtainMessage(MainActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }
    
}