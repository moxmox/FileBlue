package com.example.zemcd.fileblue;

import java.util.UUID;

public class BTS_Constants {
    public static final int STATE_NONE = 0;
    public static final int STATE_LISTENING = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED = 3;

    public static final int REQUEST_COURSE_LOCATION = 73;
    public static final int REQUEST_READ_EXTERNAL_STORAGE = 846;
    public static final int REQUEST_WRITE_EXTERNAL_STORAGE = 4561;

    public static final int REQUEST_BT_ENABLE = 174;

    public static final UUID _UUID_SECURE = UUID.fromString("b423e10d-7dcc-498d-a166-6be1a3fbef68");
    public static final String SERVICE_NAME = "BluetoothService";

    ////THREAD TRACKING CONSTANTS////
    public static final String THREAD_ACCEPT = "ACCEPT";
    public static final String THREAD_CONNECT = "CONNECT";

    ///DIALOG FRAGMENT CONSTANTS
    public static final String FRAGMENT_TAG = "FRAGMENT_TAG";

    //COLOR CONSTANTS FOR VIEWS
    public static final int CYAN = 745;

    public static final String BLUETOOTH_AUTH = "BLUETOOTH_AUTH";
    public static final String AUTH_YES = "AUTH_YES";
    public static final String AUTH_NO = "AUTH_NO";
    public static final String BLUETOOTH_AUTH_RESULT = "BLUETOOTH_AUTH_RESULT";

    //NOTIFICATION _ID FOR DOWNLOADS
    public static final int NOTIFICATION_ID = 9910;

    public static final String START_MARKER = "#%_";

}
