package com.example.android.bluetoothgatt;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

public class TimerGattProfile {

    /* Unique ids generated for this device by 'uuidgen'. Doesn't conform
     * to any SIG profile.
     */

    //Service UUID to expose our timer characteristics
    public static UUID UUID_SERVICE_TIMER =
            UUID.fromString("1706BBC0-88AB-4B8D-877E-2237916EE929");

    //Read-only characteristic providing number of seconds since offset
    public static UUID UUID_CHARACTERISTIC_ELAPSED =
            UUID.fromString("275348FB-C14D-4FD5-B434-7C3F351DEA5F");
    //Read-only characteristic, supports notifications
    public static int PROPERTIES_ELAPSED =
            BluetoothGattCharacteristic.PROPERTY_READ
                    | BluetoothGattCharacteristic.PROPERTY_NOTIFY;
    public static int PERMISSIONS_ELAPSED =
            BluetoothGattCharacteristic.PERMISSION_READ;

    //Read-write characteristic for current offset timestamp
    public static UUID UUID_CHARACTERISTIC_OFFSET =
            UUID.fromString("BD28E457-4026-4270-A99F-F9BC20182E15");
    //Read/write characteristic
    public static int PROPERTIES_OFFSET =
            BluetoothGattCharacteristic.PROPERTY_READ
                    | BluetoothGattCharacteristic.PROPERTY_WRITE;
    public static int PERMISSIONS_OFFSET =
            BluetoothGattCharacteristic.PERMISSION_READ
                    | BluetoothGattCharacteristic.PERMISSION_WRITE;

    public static String getStateDescription(int state) {
        switch (state) {
            case BluetoothProfile.STATE_CONNECTED:
                return "Connected";
            case BluetoothProfile.STATE_CONNECTING:
                return "Connecting";
            case BluetoothProfile.STATE_DISCONNECTED:
                return "Disconnected";
            case BluetoothProfile.STATE_DISCONNECTING:
                return "Disconnecting";
            default:
                return "Unknown State "+state;
        }
    }

    public static String getStatusDescription(int status) {
        switch (status) {
            case BluetoothGatt.GATT_SUCCESS:
                return "SUCCESS";
            default:
                return "Unknown Status "+status;
        }
    }

    public static byte[] getShiftedTimeValue(int timeOffset) {
        int value = Math.max(0,
                (int)(System.currentTimeMillis()/1000) - timeOffset);
        return bytesFromInt(value);
    }

    public static int unsignedIntFromBytes(byte[] raw) {
        if (raw.length < 4)
            throw new IllegalArgumentException("Cannot convert raw data to int");

        return ((raw[0] & 0xFF)
                + ((raw[1] & 0xFF) << 8)
                + ((raw[2] & 0xFF) << 16)
                + ((raw[3] & 0xFF) << 24));
    }

    public static byte[] bytesFromInt(int value) {
        //Convert result into raw bytes. GATT APIs expect LE order
        return ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(value)
                .array();
    }
}
