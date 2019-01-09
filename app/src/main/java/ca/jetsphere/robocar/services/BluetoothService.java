package ca.jetsphere.robocar.services;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import ca.jetsphere.robocar.HiddenActivity;

public class BluetoothService extends Service
{
    private static String TAG = "BluetoothService";

    private final IBinder mBinder = new LocalBinder();

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;

    private enum State { NONE, LISTEN, CONNECTING, CONNECTED };
    private ConnectThread connectThread;
    static private ConnectedThread connectedThread;
    static private State deviceState;

    private final String DEVICE_ADDRESS = "98:D3:31:FC:69:F7";
    private final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");


    /**
     *
     */
    public BluetoothService() {
    }


    /**
     *
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        deviceState = State.NONE;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

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
    private synchronized void connected(BluetoothSocket bluetoothSocket) {
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

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
    private class ConnectedThread extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private byte[] buffer;

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
            while (true) {
                try {
                    if (!encodeData(inputStream)) {
                        deviceState = BluetoothService.State.NONE;
                        connectionLost();
                        break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    connectionLost();
                    BluetoothService.this.stop();
                    break;
                }
            }
        }

        /**
         *
         */
        public void cancel() {

        }
    }

    /**
     *
     */
    private void connectionFailed() {
        BluetoothService.this.stop();
    }

    /**
     *
     */
    private void connectionLost() {
        BluetoothService.this.stop();
    }

    /**
     *
     */
    private void setState(State state) {
        BluetoothService.deviceState = state;
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
}
