package client.android2pc.android2pc_bluetooth_server;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    EditText messageEditText;
    TextView messagesTextView;
    TextView connectingTextView;
    Button connectButton;
    Button sendButton;
    Button clearButton;

    private static final String TAG = "MainActivity";
    private static final String SERVER_MAC_ADDRESS = "64:80:99:92:2F:83";
    private static final String SOCKET_UUID_STRING = "00001101-0000-1000-8000-00805F9B34FB";

    private BluetoothSocket bluetoothSocket = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        messageEditText = (EditText) findViewById(R.id.messageEditText);
        messagesTextView = (TextView) findViewById(R.id.messagesTextView);
        connectButton = (Button) findViewById(R.id.connectButton);
        connectingTextView = (TextView) findViewById(R.id.connectingTextView);
        sendButton = (Button) findViewById(R.id.sendButton);
        clearButton = (Button) findViewById(R.id.clearButton);

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectingTextView.setVisibility(View.VISIBLE);
                connectingTextView.setText(R.string.connecting);

                BluetoothSocket bluetoothSocket = getRfCOMMSocketInstance();

                if (bluetoothSocket != null && bluetoothSocket.isConnected()) {
                    connectingTextView.setVisibility(View.INVISIBLE);
                    messageEditText.setVisibility(View.VISIBLE);
                    messagesTextView.setVisibility(View.VISIBLE);
                    sendButton.setVisibility(View.VISIBLE);
                    clearButton.setVisibility(View.VISIBLE);
                    connectButton.setEnabled(false);

                    BluetoothSocketListener bsl = new BluetoothSocketListener(bluetoothSocket, new Handler(), messagesTextView);
                    Thread messageListener = new Thread(bsl);
                    messageListener.start();
                } else {
                    connectingTextView.setText(R.string.cannot_connect);
                }

            }
        });

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String message = messageEditText.getText().toString();

                if (message.length() <= 0) return;

                new SendMessageToServer().execute(message);
                messageEditText.setText("");
            }
        });

        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                messagesTextView.setText("");
            }
        });
    }

    private synchronized BluetoothSocket getRfCOMMSocketInstance() {
        if (bluetoothSocket == null || !bluetoothSocket.isConnected()) {
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            mBluetoothAdapter.enable();
            // Client knows the server MAC address
            BluetoothDevice mmDevice = mBluetoothAdapter.getRemoteDevice(SERVER_MAC_ADDRESS);

            Log.d(TAG, "got hold of remote device");
            try {
                // UUID string same used by server
                bluetoothSocket = mmDevice.createInsecureRfcommSocketToServiceRecord(UUID
                        .fromString(SOCKET_UUID_STRING));
                Log.d(TAG, "bluetooth socket created");

                mBluetoothAdapter.cancelDiscovery();    // Cancel, discovery slows connection

                bluetoothSocket.connect();
                Log.d(TAG, "connected to server");

                return bluetoothSocket;
            } catch (Exception e) {

                Log.d(TAG, "Error creating bluetooth socket");
                Log.d(TAG, e.getMessage());
                return null;
            }

        } else {
            Log.d(TAG, "bluetooth socket already exists");
            return this.bluetoothSocket;
        }
    }

    private class SendMessageToServer extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... messages) {


            final String message = messages[0];

            Log.d(TAG, "doInBackground");
            try {
                BluetoothSocket bluetoothSocket = getRfCOMMSocketInstance();

                bluetoothSocket.getOutputStream().write(message.length());
                bluetoothSocket.getOutputStream().write(message.getBytes());
                bluetoothSocket.getOutputStream().flush();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        messagesTextView.setText(messagesTextView.getText().toString() + "\nTO SERVER -> " + message);
                    }
                });

                Log.d(TAG, "Message Successfully sent to server");

            } catch (Exception e) {

                Log.d(TAG, "Error while IO operations");
                Log.d(TAG, e.getMessage());
                return "";
            }

            return "";
        }
    }

    private class MessagePoster implements Runnable {
        private TextView messagesTextView;
        private String message;

        public MessagePoster(TextView messagesTextView, String message) {
            this.messagesTextView = messagesTextView;
            this.message = message;
        }

        public void run() {
            if (message.length() >= 0)
                messagesTextView.setText(messagesTextView.getText().toString() + "\nFROM SERVER <- " + message);
        }
    }

    private class BluetoothSocketListener implements Runnable {

        private BluetoothSocket socket;
        private TextView textView;
        private Handler handler;

        public BluetoothSocketListener(BluetoothSocket socket,
                                       Handler handler, TextView textView) {
            this.socket = socket;
            this.textView = textView;
            this.handler = handler;
        }

        public void run() {
            int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];
            try {


                while (true) {
                    int len = bluetoothSocket.getInputStream().read();
                    byte[] data = new byte[len];

                    len = 0;
                    // read the message
                    while (len != data.length) {
                        int ch = bluetoothSocket.getInputStream().read(data, len, data.length - len);
                        if (ch == -1) {
                            break;
                        }
                        len += ch;
                    }

                    final String message = new String(data).trim();

                    handler.post(new MessagePoster(textView, message));
                }

            } catch (IOException e) {
                Log.d(TAG, e.getMessage());
            }
        }
    }
}
