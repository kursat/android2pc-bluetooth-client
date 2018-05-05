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
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    EditText mesajAlani;
    EditText sifreAlani;
    TextView tumMesajlar;
    TextView baglantiBilgisi;
    Button baglanButonu;
    Button gonderButonu;
    Button temizleButonu;

    private static final String TAG = "MainActivity";
    private static final String SIFRE = "123456";
    /**
     * Fikri
     */
    private static final String SERVERIN_MAC_ADRESI = "24:0A:64:86:A0:A8";
//    private static final String SERVERIN_MAC_ADRESI = "64:80:99:92:2F:83";
    private static final String SOCKET_UUID_STRING = "00001101-0000-1000-8000-00805F9B34FB";

    private BluetoothSocket bluetoothSocket = null;

    // İlk Çalışan Metod
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sifreAlani = (EditText) findViewById(R.id.sifreAlani);
        mesajAlani = (EditText) findViewById(R.id.mesajAlani);
        tumMesajlar = (TextView) findViewById(R.id.tumMesajlar);
        baglanButonu = (Button) findViewById(R.id.baglanButonu);
        baglantiBilgisi = (TextView) findViewById(R.id.baglantiBilgisi);
        gonderButonu = (Button) findViewById(R.id.gonderButonu);
        temizleButonu = (Button) findViewById(R.id.temizleButonu);

        baglanButonu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!sifreAlani.getText().toString().equals(MainActivity.SIFRE)) {

                    baglantiBilgisi.setVisibility(View.VISIBLE);
                    baglantiBilgisi.setText(R.string.wrong_password);

                    return;

                }

                // Soket bağlantısı oluşturuluyor.
                BluetoothSocket bluetoothSocket = SoketAc();

                if (bluetoothSocket != null && bluetoothSocket.isConnected()) {
                    // Arayüz değişiklileri
                    mesajAlani.setVisibility(View.VISIBLE);
                    tumMesajlar.setVisibility(View.VISIBLE);
                    gonderButonu.setVisibility(View.VISIBLE);
                    temizleButonu.setVisibility(View.VISIBLE);

                    sifreAlani.setVisibility(View.GONE);
                    baglanButonu.setVisibility(View.GONE);
                    baglantiBilgisi.setVisibility(View.GONE);

                    // Soket üzerinden gelen mesajları dinleyen thread.
                    GelenMesajlariDinle bsl = new GelenMesajlariDinle(bluetoothSocket, tumMesajlar);
                    Thread messageListener = new Thread(bsl);
                    messageListener.start();
                } else {
                    // Bağlantı yapılamadı bilgisi
                    baglantiBilgisi.setVisibility(View.VISIBLE);
                    baglantiBilgisi.setText(R.string.cannot_connect);
                }

            }
        });

        gonderButonu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String mesaj = mesajAlani.getText().toString();

                if (mesaj.length() <= 0) return;

                // Server a mesajı gönderen AsyncTask
                new ServeraGonder().execute(mesaj);
                mesajAlani.setText("");
            }
        });

        temizleButonu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tumMesajlar.setText("");
            }
        });
    }

    private synchronized BluetoothSocket SoketAc() {
        if (bluetoothSocket == null || !bluetoothSocket.isConnected()) {
            // Bluetooth adapter ı alıyoruz. Bluetooth kapalıysa açıyoruz.
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            mBluetoothAdapter.enable();
            // Server a bağlanıyoruz.
            BluetoothDevice mmDevice = mBluetoothAdapter.getRemoteDevice(SERVERIN_MAC_ADRESI);

            try {
                // Bluetooth üzerinden cihazlar arası soket oluturuluyor.
                bluetoothSocket = mmDevice.createInsecureRfcommSocketToServiceRecord(UUID
                        .fromString(SOCKET_UUID_STRING));
                Log.d(TAG, "Soket oluşturuldu");

                // Keşfetmeyi kapatıyoruz. (Bağlantı hızı için)
                mBluetoothAdapter.cancelDiscovery();

                // Sokete bağlanıyoruz.
                bluetoothSocket.connect();
                Log.d(TAG, "Servera bağlanıldı");

                return bluetoothSocket;
            } catch (Exception e) {

                Log.d(TAG, "Soket oluşturma hatası");
                Log.d(TAG, e.getMessage());
                return null;
            }

        } else {
            Log.d(TAG, "Soket zaten var");
            return this.bluetoothSocket;
        }
    }

    // Server'a mesaj gönderen AsyncTask
    private class ServeraGonder extends AsyncTask<String, Void, String> {

        // Bu metod arkaplanda çalışarak GUI'ı kilitlemeden mesajı soket üzerinden gönderiyor.
        @Override
        protected String doInBackground(String... messages) {

            final String message = messages[0];

            Log.d(TAG, "doInBackground");
            try {
                // Soketi alarak mesajı gönderiyoruz.
                BluetoothSocket bluetoothSocket = SoketAc();

                bluetoothSocket.getOutputStream().write(message.length());
                bluetoothSocket.getOutputStream().write(message.getBytes());
                bluetoothSocket.getOutputStream().flush();

                // tumMesajlar alanına gönderilen mesaj basılıyor.
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tumMesajlar.setText(tumMesajlar.getText().toString() + "\nServer'a gönderilen veri -> " + message);
                    }
                });

                Log.d(TAG, "Mesaj server a başarıyla gönderildi");

            } catch (Exception e) {

                Log.d(TAG, "Mesaj gönderme hatası");
                Log.d(TAG, e.getMessage());
                return "";
            }

            return "";
        }
    }

    // Server'dan gelen mesajları dinleyen thread
    private class GelenMesajlariDinle implements Runnable {

        private BluetoothSocket socket;
        private TextView tumMesajlar;

        public GelenMesajlariDinle(BluetoothSocket socket, TextView textView) {
            this.socket = socket;
            this.tumMesajlar = textView;
        }

        public void run() {

            try {
                // Sürekli dinliyoruz.
                while (true) {
                    int mesajUzunlugu = bluetoothSocket.getInputStream().read();
                    byte[] veri = new byte[mesajUzunlugu];

                    mesajUzunlugu = 0;
                    // Mesajı okuyoruz.
                    while (mesajUzunlugu != veri.length) {
                        int ch = bluetoothSocket.getInputStream().read(veri, mesajUzunlugu, veri.length - mesajUzunlugu);
                        if (ch == -1) {
                            break;
                        }
                        mesajUzunlugu += ch;
                    }

                    final String mesaj = new String(veri).trim();

                    // Gelen mesajı tumMesajlar alanına yazıyoruz.
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mesaj.length() >= 0)
                                tumMesajlar.setText(tumMesajlar.getText().toString() + "\nServer'dan gelen veri <- " + mesaj);
                        }
                    });
                }

            } catch (IOException e) {
                Log.d(TAG, e.getMessage());
            }
        }
    }
}
