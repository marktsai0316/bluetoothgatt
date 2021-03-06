package com.example.android.bluetoothgatt.client;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.example.android.bluetoothgatt.R;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static com.example.android.bluetoothgatt.TimerGattProfile.*;

public class ClientActivity extends Activity
        implements TimeClientCallback.ClientStatusListener {
    private static final String TAG = ClientActivity.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;

    private SparseArray<BluetoothDevice> mDevices;

    private BluetoothGatt mConnectedGatt;
    private TimeClientCallback mGattCallback;

    /* Client UI elements */
    private TextView mLatestValue;
    private TextView mCurrentOffset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);

        mLatestValue = (TextView) findViewById(R.id.latest_value);
        mCurrentOffset = (TextView) findViewById(R.id.offset_date);
        updateDateText(0);

        /*
         * Bluetooth in Android 4.3+ is accessed via the BluetoothManager,
         * rather than the old static BluetoothAdapter.getInstance()
         */
        mBluetoothManager =
                (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        mDevices = new SparseArray<BluetoothDevice>();
        mGattCallback = new TimeClientCallback(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        /*
         * We need to enforce that Bluetooth is first enabled, and take the
         * user to settings to enable it if they have not done so.
         */
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            //Bluetooth is disabled
            Intent enableBtIntent =
                    new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
            finish();
            return;
        }

        /*
         * Check for Bluetooth LE Support.  In production, our manifest entry
         * will keep this from installing on these devices, but this will
         * allow test devices or other sideloads to report whether or not
         * the feature exists.
         */
        if (!getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "No LE Support.", Toast.LENGTH_SHORT)
                    .show();
            finish();
            return;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        //Stop any active scans
        stopScan();
        //Disconnect from any active connection
        if (mConnectedGatt != null) {
            mConnectedGatt.disconnect();
            mConnectedGatt = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.scan, menu);
        //Add any device elements we've discovered to the overflow menu
        for (int i=0; i < mDevices.size(); i++) {
            BluetoothDevice device = mDevices.valueAt(i);
            menu.add(0, mDevices.keyAt(i), 0, device.getName());
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_scan:
                mDevices.clear();
                startScan();
                return true;
            default:
                //Obtain the discovered device to connect with
                BluetoothDevice device = mDevices.get(item.getItemId());
                Log.i(TAG, "Connecting to " + device.getName());
                /*
                 * Make a connection with the device using the special
                 * LE-specific connectGatt() method, passing in a callback
                 * for GATT events
                 */
                mConnectedGatt = device.connectGatt(this,
                        false,
                        mGattCallback);
                return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Select a new time to set as the base offset
     * on the GATT Server. Then write to the characteristic.
     */
    public void onUpdateClick(View v) {
        if (mConnectedGatt != null) {
            final Calendar now = Calendar.getInstance();
            TimePickerDialog dialog = new TimePickerDialog(this,
                    mTimeSetListener,
                    now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE),
                    false);
            dialog.show();
        }
    }

    private TimePickerDialog.OnTimeSetListener mTimeSetListener =
            new TimePickerDialog.OnTimeSetListener() {
        @Override
        public void onTimeSet(TimePicker view,
                              int hourOfDay,
                              int minute) {
            final Calendar now = Calendar.getInstance();
            now.set(Calendar.HOUR_OF_DAY, hourOfDay);
            now.set(Calendar.MINUTE, minute);
            now.set(Calendar.SECOND, 0);
            now.set(Calendar.MILLISECOND, 0);

            BluetoothGattCharacteristic characteristic =
                    mConnectedGatt.getService(UUID_SERVICE_TIMER)
                            .getCharacteristic(UUID_CHARACTERISTIC_OFFSET);

            int selected = (int) (now.getTimeInMillis() / 1000);
            byte[] value = bytesFromInt(selected);
            Log.d(TAG, "Writing value of size " + value.length);
            characteristic.setValue(value);

            mConnectedGatt.writeCharacteristic(characteristic);
        }
    };

    /*
     * Retrieve the current value of the time offset
     */
    public void onGetOffsetClick(View v) {
        if (mConnectedGatt != null) {
            BluetoothGattCharacteristic characteristic = mConnectedGatt
                    .getService(UUID_SERVICE_TIMER)
                    .getCharacteristic(UUID_CHARACTERISTIC_OFFSET);

            mConnectedGatt.readCharacteristic(characteristic);
            mCurrentOffset.setText("---");
        }
    }

    /*
     * Begin a scan for new servers that advertise our
     * matching service.
     */
    private void startScan() {
        //Scan for devices advertising our custom service
        ScanFilter scanFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(UUID_SERVICE_TIMER))
                .build();
        ArrayList<ScanFilter> filters = new ArrayList<ScanFilter>();
        filters.add(scanFilter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build();
        mBluetoothAdapter.getBluetoothLeScanner()
                .startScan(filters, settings, mScanCallback);
    }

    /*
     * Terminate any active scans
     */
    private void stopScan() {
        mBluetoothAdapter.getBluetoothLeScanner().stopScan(mScanCallback);
    }

    /*
     * Callback handles results from new devices that appear
     * during a scan. Batch results appear when scan delay
     * filters are enabled.
     */
    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d(TAG, "onScanResult");

            processResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.d(TAG, "onBatchScanResults: "+results.size()+" results");

            for (ScanResult result : results) {
                processResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.w(TAG, "LE Scan Failed: "+errorCode);
        }

        private void processResult(ScanResult result) {
            BluetoothDevice device = result.getDevice();
            Log.i(TAG, "New LE Device: "
                    + device.getName() + " @ " + result.getRssi());
            //Add it to the collection
            mDevices.put(device.hashCode(), device);
            //Update the overflow menu
            invalidateOptionsMenu();

            stopScan();
        }
    };

    /** UI Handlers for events from the GATT client instance */

    @Override
    public void onTimeValueChanged(int value) {
        mLatestValue.setText(String.valueOf(value));
    }

    @Override
    public void onTimeOffsetChanged(long offset) {
        updateDateText(offset);
    }

    private void updateDateText(long offset) {
        Date date = new Date(offset);
        String dateString = DateFormat.getDateTimeInstance().format(date);
        mCurrentOffset.setText(dateString);
    }
}
