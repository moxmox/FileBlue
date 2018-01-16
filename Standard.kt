package com.example.zemcd.fileblue

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.support.annotation.LayoutRes
import android.support.v4.app.ActivityCompat
import android.view.View
import android.view.ViewGroup
import android.widget.Button

fun Activity.getPermission(permissions:String, requestCode:Int) {
    val permissionsArr = arrayOf(permissions)

    if (checkSelfPermission(permissions)
            != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this,
                permissionsArr,
                requestCode)
    }

}

fun BroadcastReceiver.applyFilter(intent:String, context:Context){
    val filter = IntentFilter(intent)
    context.registerReceiver(this, filter)
}
