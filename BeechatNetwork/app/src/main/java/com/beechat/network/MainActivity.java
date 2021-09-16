package com.beechat.network;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.digi.xbee.api.DigiMeshNetwork;
import com.digi.xbee.api.RemoteXBeeDevice;
import com.digi.xbee.api.android.DigiMeshDevice;
import com.digi.xbee.api.android.connection.usb.AndroidUSBPermissionListener;
import com.digi.xbee.api.exceptions.XBeeException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/***
 *  --- MainActivity ----
 *  The class that is responsible for the main application window.
 ***/
public class MainActivity extends AppCompatActivity {

    // Constants.
    private static final int BAUD_RATE = 57600;
    private static final File root = new File(String.valueOf(Environment.getExternalStorageDirectory()));
    private static final String sFileName = "log.txt";
    private static final File gpxfile = new File(root, sFileName);
    private static final String separator = System.getProperty("line.separator");
    private static final File fdelete = new File(gpxfile.getPath());

    // Variables.
    private AndroidUSBPermissionListener permissionListener;
    private CustomDeviceAdapter remoteXBeeDeviceAdapter;
    private static String selectedDevice = null;
    private static DigiMeshDevice myDevice;
    private static ArrayList<String> dmaDevices = new ArrayList<>();

    ListView devicesListView;
    Button refreshButton;
    TextView listDevicesText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listDevicesText = (TextView) findViewById(R.id.textView);
        refreshButton = (Button)findViewById(R.id.refreshButton);

        devicesListView = (ListView)findViewById(R.id.devicesListView);
        remoteXBeeDeviceAdapter = new CustomDeviceAdapter(this, dmaDevices);
        devicesListView.setAdapter(remoteXBeeDeviceAdapter);

        // Handling an event on clicking an item from the list of available devices.
        devicesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                logOnSd("MainActivity, onItemClick(AdapterView<?> adapterView, View view, int i, long l), 82");
                selectedDevice = remoteXBeeDeviceAdapter.getItem(i);
                logOnSd("selectedDevice:"+selectedDevice.toString());
                connectToDevice(selectedDevice);
            }
        });

        // Handling and event  by clicking the "Refresh" button.
        refreshButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                logOnSd("MainActivity, refreshButton.setOnClickListener(new View.OnClickListener(), 89");
                remoteXBeeDeviceAdapter.clear();
                startScan();

            }
        });

        // Request for permission to access  phone ports.
        requestPermission();

        // Checking the conditions for permission to create and write a file with a log to the phone.
        if (shouldAskPermissions()) {
            askPermissions();
        } else {
            if (fdelete.exists()) {
                if (fdelete.delete()) {
                    System.out.println("File log.txt deleted :" + gpxfile.getPath());
                } else {
                    System.out.println("file log.txt not deleted :" + gpxfile.getPath());
                }
            }
        }
        logOnSd("MainActivity, onCreate(Bundle savedInstanceState)), 108");
    }

    /***
     *  --- logOnSd(String) ----
     *  The function of writing information to a log file on the phone.
     *
     *  @param sBody Logged message text.
     ***/
    public static void logOnSd(String sBody) {
        try {
            if (!root.exists()) {
                root.mkdirs();
            }
            Date currentTime = Calendar.getInstance().getTime();
            FileWriter writer = new FileWriter(gpxfile.getAbsoluteFile(), true);
            writer.append(separator);
            writer.append(currentTime.toString() +" ,"+sBody);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /***
     *  --- shouldAskPermissions() ----
     *  The function of checking for permission to create and write information to the phone.
     ***/
    protected boolean shouldAskPermissions() {
        if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            return false;
        }
        int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return  (permission != PackageManager.PERMISSION_GRANTED);
    }

    /***
     *  --- askPermissions() ----
     *  The function of requesting permission to access the internal storage of the phone.
     ***/
    protected void askPermissions() {
        String[] permissions = {
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE"
        };
        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, permissions, 1);
        }
    }

    /***
     *  --- requestPermission() ----
     *  The function of requesting permission to access the ports of the phone.
     ***/
    private void requestPermission() {
        logOnSd("MainActivity, requestPermission(), 162");
        permissionListener = new AndroidUSBPermissionListener() {
            @Override
            public void permissionReceived(boolean permissionGranted) {
                if (permissionGranted)
                    System.out.println("User granted USB permission.");
                else
                    System.out.println("User rejected USB permission.");
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        logOnSd("MainActivity, onResume(), 177");
        remoteXBeeDeviceAdapter.clear();
        //startScan();
    }

    /***
     *  --- startScan() ----
     *  The function of starting scanning for available Xbee devices.
     ***/
    private void startScan() {
        logOnSd("MainActivity, startScan(), 183");
        myDevice = new DigiMeshDevice(MainActivity.this, BAUD_RATE, permissionListener);
        listnodes(myDevice);
        remoteXBeeDeviceAdapter.notifyDataSetChanged();
    }

    /***
     *  --- getDMDevice() ----
     *  The function of gaining access to your device..
     ***/
    public static DigiMeshDevice getDMDevice() {
        logOnSd("MainActivity, getDMDevice(), 190");
        return myDevice;
    }

    /***
     *  --- getSelectedDevice() ----
     *  The function of gaining access to the selected device
     ***/
    public static String getSelectedDevice() {
        logOnSd("MainActivity, getSelectedDevice(), 195");
        return selectedDevice;
    }

    /***
     *  --- connectToDevice(String) ----
     *  The function of establishing a connection with the selected device.
     *
     *  @param device Selected device number.
     ***/
    private void connectToDevice(final String device) {
        logOnSd("MainActivity, connectToDevice(final String device), 200");
        final ProgressDialog dialog = ProgressDialog.show(this, getResources().getString(R.string.connecting_device_title),
                getResources().getString(R.string.connecting_device_description), true);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    logOnSd("MainActivity, connectToDevice(final String device), 208");
                    logOnSd("myDevice:"+myDevice.toString());
                    myDevice.open();

                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            logOnSd("MainActivity, connectToDevice(final String device), 214");
                            dialog.dismiss();
                            Intent intent = new Intent(MainActivity.this, ChatActivity.class);
                            startActivity(intent);
                            logOnSd("MainActivity, connectToDevice(final String device), 218");
                        }
                    });
                } catch (final XBeeException e) {
                    logOnSd("MainActivity, connectToDevice(final String device), 222");
                    e.printStackTrace();
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            logOnSd("MainActivity, connectToDevice(final String device), 227");
                            dialog.dismiss();
                            new AlertDialog.Builder(MainActivity.this).setTitle(getResources().getString(R.string.error_connecting_title))
                                    .setMessage(getResources().getString(R.string.error_connecting_description, e.getMessage()))
                                    .setPositiveButton(android.R.string.ok, null).show();
                        }
                    });
                    myDevice.close();
                }
                logOnSd("MainActivity, connectToDevice(final String device), 237");
            }
        }).start();
        logOnSd("MainActivity, connectToDevice(final String device), 240");
    }

    /***
     *  --- CustomDeviceAdapter ----
     *  The class that initializes the list of available devices.
     ***/
    private class CustomDeviceAdapter extends ArrayAdapter<String> {
        private Context context;

        CustomDeviceAdapter(@NonNull Context context, ArrayList<String> devices) {
            super(context, -1, devices);
            this.context = context;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            String device = dmaDevices.get(position);

            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(40, 30, 40, 30);

            TextView nameText = new TextView(context);
            nameText.setText(device);
            nameText.setTypeface(nameText.getTypeface(), Typeface.BOLD);
            nameText.setTextSize(18);
            layout.addView(nameText);

            return layout;
        }
    }

    /***
     *  --- listnodes(DigiMeshDevice) ----
     *  The function searches for available devices for the specified device..
     *
     *  @param myDevice Current device.
     ***/
    public static List<RemoteXBeeDevice> listnodes(DigiMeshDevice myDevice) {
        logOnSd("MainActivity, listnodes(DigiMeshDevice myDevice), 264");
        List<RemoteXBeeDevice> devices = null;

        if (myDevice.isOpen() == true) {
            myDevice.close();
        }

        try {
            myDevice.open();

            DigiMeshNetwork network = (DigiMeshNetwork) myDevice.getNetwork();
            network.startDiscoveryProcess();

            while (network.isDiscoveryRunning()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            devices = network.getDevices();
            int i = 0;
            while (i<devices.size()) {
                dmaDevices.add(devices.get(i).get64BitAddress().toString());
                i=i+1;
            }
        } catch (XBeeException e) {
            e.printStackTrace();
            myDevice.close();
        } finally {
            myDevice.close();
        }
        return devices;
    }

}
