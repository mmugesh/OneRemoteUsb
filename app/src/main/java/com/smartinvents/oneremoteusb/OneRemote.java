package com.smartinvents.oneremoteusb;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;

import java.util.LinkedList;
import java.util.Queue;

public class OneRemote {

    // variables
    private FTDriver mSerial = null;
    private boolean ready = false;
    private boolean mUsbReceiverRegistered = false;
    private int SERIAL_BAUDRATE;
    private Activity activity;
    private Queue<byte[]> sendQueue = new LinkedList<byte[]>();

    private final int ONEREMOTE_BUFFER_SIZE = 62;

    private final byte[] CMD_RESET = new byte[] { 0, 0, 0, 0, 0 };  	  // 5 mal 0x00
    private final byte[] CMD_SAMPLEMODE = new byte[] { 's' };       	  // 'c'
    private final byte[] CMD_TRANSMIT = getCommandBytes("03");      	  // 0x03
    private final byte[] CMD_BYTE_COUNT_REPORT = getCommandBytes("24");   // 0x24
    private final byte[] CMD_NOTIFY_ON_COMPLETE = getCommandBytes("25");  // 0x25
    private final byte[] CMD_HANDSHAKE = getCommandBytes("26");			  // 0x26

	/*
	 * Public API
	 */

    /**
     * constructor
     */
    public OneRemote(Activity acitivity) {
        this.activity = acitivity;
    }

    /**
     * clean up
     */
    public void Close() {
        if (mSerial != null) {
            mSerial.end();
            mSerial = null;
        }
        if (mUsbReceiverRegistered) {
            activity.unregisterReceiver(mUsbReceiver);
        }
    }

    /**
     *  Initializes the connection. Returns whether the connection was successful.
     */
    public boolean init() {
        return init(FTDriver.BAUD115200);
    }

    /**
     * Initializes the connection. Returns whether the connection was successful.
     */
    public boolean init(int baudrate) {
        if (ready)
            throw new RuntimeException("OneRemote is ready and initialized.");

        if (mSerial == null) {

            SERIAL_BAUDRATE = baudrate;
            mSerial = new FTDriver((UsbManager)activity.getSystemService(Context.USB_SERVICE));


            String intentName = activity.getApplicationInfo().packageName + ".USB_PERMISSION";
            //	MainActivity.log("intentName = " + intentName);
            System.out.println("intentName = " + intentName);
            PendingIntent permissionIntent = PendingIntent.getBroadcast(activity, 0, new Intent(intentName), 0);
            //	PendingIntent permissionIntent = PendingIntent.getBroadcast(activity, 0, new Intent(ACTION_USB_PERMISSION), 0);
            mSerial.setPermissionIntent(permissionIntent);

            // listen for new devices
            IntentFilter filter = new IntentFilter();
            filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            activity.registerReceiver(mUsbReceiver, filter);
            mUsbReceiverRegistered = true;
        }

        if(mSerial.begin(SERIAL_BAUDRATE)) {
            initConnection();

            return true;
        } else {

            return false;
        }
    }

    /**
     * Resets the connection through, useful for unexpected errors.
     */
    public void reset() {
        ready = false;
        initConnection();
    }

    /**
     * Sends a command in the format '00 01 a3 ff ff 89 e8 bf ac '. He must necessarily conclude with 'ff ff'!
     */
    public void sendCommandAsync(String command) {

        sendInternal( getCommandBytes(command) );
    }

	/*
	 * internal methods
	 */

    private void initConnection() {
        if (ready) {
            throw new RuntimeException("OneRemote is already ready (init was carried out again?)");
        }

        mSerial.write(CMD_RESET);


        mSerial.write(CMD_SAMPLEMODE);

        readSampleMode();

        mSerial.write(CMD_BYTE_COUNT_REPORT);


        mSerial.write(CMD_NOTIFY_ON_COMPLETE);


        mSerial.write(CMD_HANDSHAKE);


        ready = true;
    }

    private byte[] readAnswer() {
        // TODO Somehow better, or can this continue?
        byte[] buffer = new byte[4096];
        int len = mSerial.read(buffer);
        if (len == 0) {

            return new byte[0];
        }
        // Start debug
        String text = "";
        String bytes = "";
        String hex = "";
        for (int i = 0; i < len; i++) {
            text = text + (char)buffer[i];
            bytes = bytes + buffer[i] + " ";
            hex = hex + String.format("0x%02X", (buffer[i])) + " ";
        }



        byte[] reply = new byte[len];
        System.arraycopy(buffer, 0, reply, 0, len);
        return reply;
    }

    private void readSampleMode() {
        byte[] reply = readAnswer();
        String text = new String(reply);
        if (!text.equals("S01")) {
            throw new RuntimeException("Sample Mode has not responded with S01.");
        }
    }

    private void readHandshake() {
        byte[] reply = readAnswer();
        if (reply.length != 1 || reply[0] != ONEREMOTE_BUFFER_SIZE) {
            throw new RuntimeException("The handshake has not responded as expected.");
        }
    }

    private void readTransmitCount(int expectedLength) {
        byte[] reply = readAnswer();
        if (reply.length != 3 || reply[0] != 't') {
            throw new RuntimeException("The transmit count has not responded as expected.");
        }
        int sendCount = reply[2] < 0 ? reply[2] + 256 : reply[2];
        int iterator = reply[1];
        for (int i = 0; i < iterator; i++) {
            expectedLength = expectedLength - 256;
        }
        if (sendCount != expectedLength) {
            throw new RuntimeException("The transmit count has not responded as expected:" + sendCount + "/" + expectedLength);
        }
    }

    private void readNotifyOnComplete() {
        byte[] reply = readAnswer();
        if (reply.length != 1 || reply[0] != 'C') {
            throw new RuntimeException("The Notify on complete has not responded as expected.");
        }
    }

    // Converts a command in hex String format into a byte array to
    private byte[] getCommandBytes(String cmd) {
        String[] partSplit = cmd.split(" ");
        byte bytes[] = new byte[partSplit.length];
        for (int i = 0; i < partSplit.length; i++) {
            bytes[i] = (byte) ((Character.digit(partSplit[i].charAt(0), 16) << 4) + Character.digit(partSplit[i].charAt(1), 16));
        }
        return bytes;
    }

    // Asynchronously sends a command
    private void sendInternal(final byte[] command) {
        if (!ready)
            throw new RuntimeException("OneRemote is not ready (Init was successful?)");

        if (command[command.length-1] != -1 && command[command.length-2] != -1) {
            throw new RuntimeException("The command does not end with 'ff ff'.");
        }

        // Add command to the queue. The execution is asynchronous.
        sendQueue.add(command);

        new Thread(new Runnable() {
            @Override
            public void run() {
                sendCommandFromQueue();
            }
        }).start();
    }

    private synchronized void sendCommandFromQueue() {
        byte[] command = sendQueue.poll();
        try {
            internalSendCommand(command);
        } catch (Exception e) {

            byte[] buffer;
            do {
                buffer = readAnswer();
            } while(buffer.length != 0);

        }
    }

    private void internalSendCommand(byte[] command) {
        if (command != null) {
            mSerial.write(CMD_TRANSMIT);

            readHandshake();

            int iFull = command.length / ONEREMOTE_BUFFER_SIZE;
            int iRest = command.length % ONEREMOTE_BUFFER_SIZE;

            // Send Buffer Full
            for(int i = 0; i < iFull; i++) {
                byte[] buffer = new byte[ONEREMOTE_BUFFER_SIZE];
                System.arraycopy(command, i * ONEREMOTE_BUFFER_SIZE, buffer, 0, ONEREMOTE_BUFFER_SIZE);
                mSerial.write(buffer);
//				MainActivity.log("fullbuffer sent: " + ONEREMOTE_BUFFER_SIZE);

                readHandshake();
            }

            // Send The Rest
            if (iRest != 0) {
                byte[] buffer = new byte[iRest];
                System.arraycopy(command, iFull * ONEREMOTE_BUFFER_SIZE, buffer, 0, iRest);

                mSerial.write(buffer);

                readHandshake();
            }

            readTransmitCount(command.length);

            readNotifyOnComplete();


        }
    }

    // BroadcastReceiver when insert/remove the device USB plug into/from a USB port
    private BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {

                mSerial.usbAttached(intent);
                mSerial.begin(SERIAL_BAUDRATE);
                initConnection();
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {

                ready = false;
                mSerial.usbDetached(intent);
                mSerial.end();
                mSerial = null;
            }
        }
    };

}
