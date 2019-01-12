package ca.jetsphere.robocar.services;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import ca.jetsphere.robocar.R;
import ca.jetsphere.robocar.RoboCarApplication;
import ca.jetsphere.robocar.activities.AbstractActivity;
import ca.jetsphere.robocar.activities.HiddenActivity;

public class BluetoothService extends Service
{
    private static String TAG = "BluetoothService";

    private final IBinder mBinder = new LocalBinder();
    private static Handler mHandler = null;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;

    private enum State { NONE, LISTEN, CONNECTING, CONNECTED };
    static final public int DISCONNECT = 0;
    static final public int CONNECT    = 1;
    static final public int SEND       = 2;
    static final public int RECEIVE    = 3;

    private ConnectThread connectThread;
    static private ConnectedThread connectedThread;
    static private State deviceState;

    private final String DEVICE_ADDRESS = "98:D3:31:FC:69:F7";
    private final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    Messenger mMessenger;

    /**
     *
     */
    public BluetoothService() {
    }

    /**
     *
     */
    @Override
    public void onCreate() {
        super.onCreate();

        deviceState = State.NONE;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Toast.makeText(getApplicationContext(), "binding", Toast.LENGTH_SHORT).show();
        mMessenger = new Messenger(new IncomingHandler(this));
        return mMessenger.getBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Start Command Called: " + startId);

        if (bluetoothAdapter != null) {
            if(!bluetoothAdapter.isEnabled()) {
                Intent hiddenIntent = new Intent(this, HiddenActivity.class);
                hiddenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(hiddenIntent);
            }
            else {
                connectToDevice();
            }
        }

        String stopService = intent.getStringExtra("stopservice");
        if (stopService != null && stopService.length() > 0) {
            stop();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stop();
        super.onDestroy();
    }

    @Override
    public boolean stopService(Intent name) {
        setState(State.NONE);
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        bluetoothAdapter.cancelDiscovery();
        return super.stopService(name);
    }

    /**
     *
     */
    public class LocalBinder extends Binder {
        BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    /**
     *
     */
    private synchronized void connectToDevice() {
        BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS);

        if (deviceState == State.CONNECTING) {
            if (connectThread != null) {
                connectThread.cancel();
                connectThread = null;
            }
        }

        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        connectThread = new ConnectThread(bluetoothDevice);
        connectThread.start();

        setState(State.CONNECTING);
    }

    /**
     *
     */
    private class ConnectThread extends Thread
    {
        private final BluetoothDevice bluetoothDevice;
        private final BluetoothSocket bluetoothSocket;

        /**
         *
         */
        public ConnectThread(BluetoothDevice bluetoothDevice) {
            this.bluetoothDevice = bluetoothDevice;
            BluetoothSocket tmp = null;

            try {
                tmp = bluetoothDevice.createRfcommSocketToServiceRecord(PORT_UUID);
            } catch (IOException e) { Log.e(TAG, "close socket failed", e); }

            bluetoothSocket = tmp;
        }

        @Override
        public void run() {
            setName("ConnectThread");

            bluetoothAdapter.cancelDiscovery();

            try {
                bluetoothSocket.connect();
            } catch (IOException e) {
                try {
                    bluetoothSocket.close();
                } catch (IOException ec) { ec.printStackTrace(); }

                connectionFailed();
                return;
            }

            synchronized (BluetoothService.this) {
                connectedThread = null;
            }
            connected(bluetoothSocket);
        }

        /**
         *
         */
        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) { Log.e(TAG, "close socket failed", e); }
        }
    }

    /**
     *
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        /**
         *
         */
        public ConnectedThread(BluetoothSocket bluetoothSocket) {
            this.bluetoothSocket = bluetoothSocket;
            InputStream inTmp = null;
            OutputStream outTmp = null;

            try {
                inTmp = bluetoothSocket.getInputStream();
                outTmp = bluetoothSocket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "could not create sockets", e);
            }

            inputStream = inTmp;
            outputStream = outTmp;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    bytes = inputStream.read(buffer);
                    // Send the obtained bytes to the UI Activity
                    mHandler.obtainMessage(AbstractActivity.MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                } catch (Exception e) {
                    e.printStackTrace();
                    connectionLost();
                    break;
                }
            }
        }

        /**
         *
         */
        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) { Log.e(TAG, "close() of connect socket failed", e); }
        }
    }

    /**
     *
     */
    private synchronized void connected(BluetoothSocket bluetoothSocket) {
        // Cancel the thread that completed the connection
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        connectedThread = new ConnectedThread(bluetoothSocket);
        connectedThread.start();

        setState(State.CONNECTED);
    }

    /**
     *
     */
    private void connectionFailed() {
        BluetoothService.this.stop();

        if (mHandler == null) return;

        Message msg = mHandler.obtainMessage(AbstractActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(AbstractActivity.TOAST, getString(R.string.error_connect_failed));
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /**
     *
     */
    private void connectionLost() {
        BluetoothService.this.stop();

        if (mHandler == null) return;

        Message msg = mHandler.obtainMessage(AbstractActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(AbstractActivity.TOAST, getString(R.string.error_connect_lost));
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /**
     *
     */
    private void setState(State state) {
        BluetoothService.deviceState = state;

        if (mHandler == null) return;

        mHandler.obtainMessage(AbstractActivity.MESSAGE_STATE_CHANGE, state.ordinal(), -1).sendToTarget();
    }

    /**
     *
     */
    public synchronized void stop() {
        setState(State.NONE);

        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        if (bluetoothAdapter != null) {
            bluetoothAdapter.cancelDiscovery();
        }

        stopSelf();
    }

    /**
     * Handler of incoming messages from clients.
     */
    static class IncomingHandler extends Handler {
        private Context applicationContext;

        IncomingHandler(Context context) {
            applicationContext = context.getApplicationContext();
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case DISCONNECT:
                    Toast.makeText(applicationContext, "Disconnect!", Toast.LENGTH_SHORT).show();
                    break;
                case CONNECT:
                    Toast.makeText(applicationContext, "Connect!", Toast.LENGTH_SHORT).show();
                    break;
                case SEND:
                    Toast.makeText(applicationContext, msg.obj.toString(), Toast.LENGTH_SHORT).show();
                    break;
                case RECEIVE:
                    Toast.makeText(applicationContext, "Receive!", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    }
}
