package com.palmsens.msbluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.UUID;

public class BluetoothConnectionService {
    private static final String TAG = "BTConnectionService";
    private final BluetoothAdapter mBluetoothAdapter;
    private final Handler mHandler;
    private boolean mThreadisStopped;

    private BluetoothDevice mDevice;
    private UUID mUUID;
    private BluetoothSocket mSocket;
    private InputStream mInStream;
    private OutputStream mOutStream;

    public BluetoothConnectionService(Context context, Handler handler) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mHandler = handler;                                             // The handler to post back messages to the main activity
    }

    /**
     * <Summary>
     *     Called from the MSBluetoothActivity on click of button Connect.
     *     Sets the selected device and the UUID of the device and starts a new runnable to connect to the device.
     * </Summary>
     * @param device  The selected bluetooth device
     * @param uuid    The UUID of the selected device
     */
    public void startClient(BluetoothDevice device, UUID uuid) {
        Log.d(TAG, "startClient: Started.");
        mDevice = device;
        mUUID = uuid;
        new Thread(connectedThread).start();                            // Starts a new runnable thread to connect to the device
    }

    /**
     * <Summary>
     *     Called from the MSBluetoothActivity on click of Disconnect.
     *     Marks that the thread is stopped to enable closing of the socket from within the runnable thread (connectedThread)
     * </Summary>
     */
    public void disconnect() {
        mThreadisStopped = true;
    }

    /**
     * <Summary>
     *     Closes the socket and sets the instream and out stream to null to shut down the connection when the device is disconnected.
     * </Summary>
     */
    public void closeSocket() {
        try {
            mSocket.close();
            mInStream = null;
            mOutStream = null;
        } catch (IOException e) { }
    }

    /**
     * <Summary>
     *     Called from the MSBluetoothActivity for sending the version command and sending the script file.
     *     Writes to the output stream of the bluetooth socket.
     * </Summary>
     * @param bytes
     */
    public void write(byte[] bytes) {
        String text = new String(bytes, Charset.defaultCharset());
        Log.d(TAG, "write: Writing to outputstream: " + text);
        try {
            mOutStream.write(bytes);
        } catch (IOException e) {
            mHandler.obtainMessage(MSBluetoothActivity.MESSAGE_TOAST, -1, -1, "Error writing to output stream".getBytes())
                    .sendToTarget();
        }
    }

    /**
     * <Summary>
     *     A thread implementing the Runnable to connect to a device, listen to the response continuously and post the messages back to the main activity.
     *     Creates a RfcommSocket and connects to the bluetooth device using that socket.
     *     The input and output memory streams of this socket is used to perform read and write operations on the device.
     *     If the device is disconnected, the thread is stopped and socket is closed.
     * </Summary>
     */
    private Runnable connectedThread = new Runnable() {

        String mReadLine = "";
        private boolean connectDevice() {

            boolean isConnected = false;
            BluetoothSocket tmp = null;

            try {
                Log.d(TAG, "ConnectedThread: Trying to create InsecureRfcomSocket using UUID: " + mDevice.toString() + mUUID);
                tmp = mDevice.createInsecureRfcommSocketToServiceRecord(mUUID);
            } catch (IOException e) {
                Log.e(TAG, "ConnectedThread: Could not create InsecureRfcomSocket " + e.getMessage());
            }

            mSocket = tmp;

            mBluetoothAdapter.cancelDiscovery();                                    // Always cancel discovery because it will slow down a connection

            try {
                // This is a blocking call and will only return on successful connection or an exception, hence in a runnable
                mSocket.connect();
                isConnected = true;
                mInStream =  mSocket.getInputStream();
                mOutStream = mSocket.getOutputStream();
                mHandler.obtainMessage(MSBluetoothActivity.MESSAGE_SOCKET_CONNECTED, -1, -1, "Could not connect to device".getBytes())
                        .sendToTarget();
                Log.d(TAG, "run: ConnectedThread connected.");
            } catch (IOException e) {
                // Close the socket
                try {
                    mSocket.close();
                    isConnected = false;
                    Log.d(TAG, "run: Closed Socket.");
                    mHandler.obtainMessage(MSBluetoothActivity.MESSAGE_SOCKET_CLOSED, -1, -1, "Could not connect to device".getBytes())
                            .sendToTarget();
                } catch (IOException e1) {
                    Log.e(TAG, "mConnectedThread: run: Unable to close connection in socket " + e1.getMessage());
                }
                Log.d(TAG, "run: ConnectedThread: Could not connect to UUID: " + mUUID);
            }
            return isConnected;
        }

        @Override
        public void run() {
            int i;
            int offset = 0;
            int readSize;   //bytes returned from read
            String rchar = "";
            byte[] rbuf = new byte[1];

            if(connectDevice())
            {
                mThreadisStopped = false;
                // Keep listening to the InputStream until an exception occurs
                while (!mThreadisStopped) {
                    // Read from the InputStream
                    try {
                        if (mInStream != null && mInStream.available() > 0) {
                            readSize = mInStream.read(rbuf, offset, 1);                 // Reads a character from the socket's in stream
                            rchar = new String(rbuf);
                            mReadLine += rchar;                                             // A line of response string is formed until new line is encountered

                            if (rchar.equals("\n")) {
                                mHandler.post(new Runnable() {
                                    final String line = mReadLine;

                                    @Override
                                    public void run() {
                                        // Send the obtained bytes to the UI Activity
                                        mHandler.obtainMessage(MSBluetoothActivity.MESSAGE_READ, -1, -1, line.getBytes())
                                                .sendToTarget();
                                    }
                                });
                                mReadLine = "";
                            }
                        }
                    } catch (IOException e) {
                        mHandler.obtainMessage(MSBluetoothActivity.MESSAGE_TOAST, -1, -1, "Error reading input stream".getBytes())
                                .sendToTarget();
                        break;
                    }
                }
                closeSocket();                                                                 // Closes the socket upon disconnecting the device
            }
        }
    };
}
























