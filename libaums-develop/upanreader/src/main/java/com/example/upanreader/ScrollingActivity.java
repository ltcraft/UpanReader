package com.example.upanreader;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.server.http.UsbFileHttpServerService;

import java.io.IOException;

public class ScrollingActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {

    private ListView listView;
    private UsbMassStorageDevice device;
    private Intent serviceIntent = null;
    private UsbFileListAdapter adapter;
    //private static final String TAG = ScrollingActivity.class.getSimpleName();
    private static final String TAG = "UpanReader";
    private static final String ACTION_USB_PERMISSION = "com.github.mjdev.libaums.USB_PERMISSION";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scrolling);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        serviceIntent = new Intent(this, UsbFileHttpServerService.class);
        listView = (ListView) findViewById(R.id.list_view);
        listView.setOnItemClickListener(this);

        discoverDevice();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }

    /**
     * Searches for connected mass storage devices, and initializes them if it
     * could find some.
     */
    private void discoverDevice() {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbMassStorageDevice[] devices = UsbMassStorageDevice.getMassStorageDevices(this);

        if (devices.length == 0) {
            Log.w(TAG, "no device found!");
            android.support.v7.app.ActionBar actionBar = getSupportActionBar();
            actionBar.setTitle("No device");
            listView.setAdapter(null);
            return;
        }

        // we only use the first device
        // 我们只使用第一个USB设备
        device = devices[0];

        UsbDevice usbDevice = (UsbDevice) getIntent().getParcelableExtra(UsbManager.EXTRA_DEVICE);

        if (usbDevice != null && usbManager.hasPermission(usbDevice)) {
            Log.d(TAG, "received usb device via intent");
            // requesting permission is not needed in this case
            setupDevice();
        } else {
            // first request permission from user to communicate with the
            // underlying
            // UsbDevice
            // TODO: 2016/9/12 下面这些是什么作用。
            PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
                    ACTION_USB_PERMISSION), 0);
            usbManager.requestPermission(device.getUsbDevice(), permissionIntent);
        }
    }

    /**
     * Sets the device up and shows the contents of the root directory.
     */
    private void setupDevice() {
        try {
            device.init();

            // we always use the first partition of the device
            FileSystem fs = device.getPartitions().get(0).getFileSystem();
            Log.d(TAG, "Capacity: " + fs.getCapacity());
            Log.d(TAG, "Occupied Space: " + fs.getOccupiedSpace());
            Log.d(TAG, "Free Space: " + fs.getFreeSpace());
            UsbFile root = fs.getRootDirectory();

            ActionBar actionBar = getSupportActionBar();
            actionBar.setTitle(fs.getVolumeLabel());

            listView.setAdapter(adapter = new UsbFileListAdapter(this, root));
        } catch (IOException e) {
            Log.e(TAG, "error setting up device", e);
        }

    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_scrolling, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


}
