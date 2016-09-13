package com.example.upanreader;

import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
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
import android.widget.Toast;

import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.server.http.UsbFileHttpServerService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class ScrollingActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {

    private ListView listView;
    private UsbMassStorageDevice device;
    private Intent serviceIntent = null;
    private UsbFileListAdapter adapter;
    private UsbFileHttpServerService serverService;
    //private static final String TAG = ScrollingActivity.class.getSimpleName();
    private static final String TAG = "UpanReader";
    private static final String ACTION_USB_PERMISSION = "com.github.mjdev.libaums.USB_PERMISSION";

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {

                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {

                    if (device != null) {
                        setupDevice();
                    }
                }

            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                Log.d(TAG, "USB device attached");

                // determine if connected device is a mass storage devuce
                if (device != null) {
                    discoverDevice();
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                Log.d(TAG, "USB device detached");

                // determine if connected device is a mass storage devuce
                if (device != null) {
                    if (ScrollingActivity.this.device != null) {
                        ScrollingActivity.this.device.close();
                    }
                    // check if there are other devices or set action bar title
                    // to no device if not
                    discoverDevice();
                }
            }

        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        serviceIntent = new Intent(this, UsbFileHttpServerService.class);

        setContentView(R.layout.activity_scrolling);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        serviceIntent = new Intent(this, UsbFileHttpServerService.class);
        listView = (ListView) findViewById(R.id.list_view);
        listView.setOnItemClickListener(this);

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, filter);

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
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

        //FileChannel fin = null;

        UsbFile file = adapter.getItem(position);
        if(!file.isDirectory()){
            File dir = new File(getExternalFilesDir(null),file.getName());
            //File dir = getExternalFilesDir(null);
            FileChannel fout = null;

            long length = file.getLength();
            /**
             * capacity的数值不能太小，似乎是应该大于512.设置的数值大了，就没事了。
             */
            int capacity = 1024*8;
            //int capacity = 26;
            ByteBuffer buffer=ByteBuffer.allocate(capacity);
            try {
                fout = new FileOutputStream(dir).getChannel();
                long remainLength = length;
                long finishPosition = 0;
                while(remainLength >= 0){
                    file.read(finishPosition,buffer);
                    buffer.flip();
                    if(remainLength<capacity){
                        buffer.limit((int)remainLength);
                    }
                    fout.write(buffer);
                    buffer.clear();
                    remainLength = remainLength - capacity;
                    finishPosition = finishPosition + capacity;
                    System.out.println(remainLength);
                    System.out.println(dir.toString());
                }

//                file.read(5,buffer);
//                buffer.flip();
//                fout.write(buffer);
//                buffer.clear();

                Toast.makeText(this,"传输完成",Toast.LENGTH_SHORT).show();

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if(fout != null) {
                        fout.close();
                    }
                } catch(IOException e) {
                    e.printStackTrace();
                }
            }

            //startHttpServer(file);
        }

    }

    private void startHttpServer(final UsbFile file) {

        Log.d(TAG, "starting HTTP server");

        if(serverService == null) {
            Toast.makeText(ScrollingActivity.this, "serverService == null!", Toast.LENGTH_LONG).show();
            return;
        }

        if(serverService.isServerRunning()) {
            Log.d(TAG, "Stopping existing server service");
            serverService.stopServer();
        }

        // now start the server
        try {
            serverService.startServer(file);
            Toast.makeText(ScrollingActivity.this, "HTTP server up and running", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Log.e(TAG, "Error starting HTTP server", e);
            Toast.makeText(ScrollingActivity.this, "Could not start HTTP server", Toast.LENGTH_LONG).show();
        }

        if(file.isDirectory()) {
            // only open activity when serving a file
            return;
        }

        Intent myIntent = new Intent(android.content.Intent.ACTION_VIEW);
        myIntent.setData(Uri.parse(serverService.getServer().getBaseUrl() + file.getName()));
        try {
            startActivity(myIntent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(ScrollingActivity.this, "Could no find an app for that file!",
                    Toast.LENGTH_LONG).show();
        }
    }

    ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "on service connected " + name);
            UsbFileHttpServerService.ServiceBinder binder = (UsbFileHttpServerService.ServiceBinder) service;
            serverService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "on service disconnected " + name);
            serverService = null;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();

        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();

        unbindService(serviceConnection);
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
