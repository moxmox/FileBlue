package com.example.zemcd.fileblue

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*

class DeviceListActivity : AppCompatActivity(){

    val TAG = "DeviceListActivity"

    var isConnected = false

    lateinit var mBtAdapter:BluetoothAdapter
    lateinit var mNewDeviceArrayAdapter:ArrayAdapter<String>
    lateinit var mPairedDeviceArrayAdapter:ArrayAdapter<String>

    lateinit var mNewLabel:TextView
    lateinit var mPairedLabel:TextView
    lateinit var mNewListView:ListView
    lateinit var mPairedListView:ListView

    lateinit var mScanButton:Button

    val mDevices:MutableList<BluetoothDevice> = mutableListOf()

    inner class SyncedEnableTask : AsyncTask<Void, Void,Void>(){
        override fun doInBackground(vararg params: Void?): Void? {
            if (!mBtAdapter.isEnabled){
                mBtAdapter.enable()
                while (!mBtAdapter.isEnabled){
                    Thread.sleep(100)
                }
            }
            return null
        }

        override fun onPostExecute(result: Void?) {
            populatePairedList()
        }

    }

    inner class SyncedTask(val fn:()->Unit, val endTask:()->Unit) : AsyncTask<Void, Void, Void>() {
        override fun doInBackground(vararg params: Void?): Void? {
            fn()
            return null
        }

        override fun onPostExecute(result: Void?) {
            super.onPostExecute(result)
            endTask()
        }

    }

    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)

        mBtAdapter = BluetoothAdapter.getDefaultAdapter()
        mNewDeviceArrayAdapter  = ArrayAdapter(this, R.layout.device_label)
        mPairedDeviceArrayAdapter  = ArrayAdapter(this, R.layout.device_label)

        mNewLabel = findViewById(R.id.title_new_devices) as TextView
        mPairedLabel = findViewById(R.id.title_paired_devices) as TextView

        mNewListView = findViewById(R.id.new_devices) as ListView
        mNewListView.adapter = mNewDeviceArrayAdapter
        mNewListView.onItemClickListener = mTouchListener
        mPairedListView = findViewById(R.id.paired_devices) as ListView
        mPairedListView.adapter = mPairedDeviceArrayAdapter
        mPairedListView.onItemClickListener = mTouchListener

        mScanButton = findViewById(R.id.button_scan) as Button
        mScanButton.setOnClickListener {discover()}

        //get permissions and notify user of bluetooth usage
        if(checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){//all perms granted
            userAgreement()
        }else if(checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED){
            getPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE, BTS_Constants.REQUEST_READ_EXTERNAL_STORAGE)
    }else if(checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_DENIED) {
                getPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION, BTS_Constants.REQUEST_COURSE_LOCATION)
        }else if(checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED){
            getPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, BTS_Constants.REQUEST_WRITE_EXTERNAL_STORAGE)
        }
        // the above permissions checks need to be revisited and corrected. this appears sloppy and also may follow correct logic

        val c = applicationContext

        mReceiver.applyFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED, c)
        mReceiver.applyFilter(BluetoothDevice.ACTION_FOUND, c)
        mReceiver.applyFilter(BluetoothDevice.ACTION_ACL_CONNECTED, c)
        mReceiver.applyFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED, c)

        BluetoothService.start(c)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when(requestCode){
            BTS_Constants.REQUEST_COURSE_LOCATION -> {
                if (grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    getPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE, BTS_Constants.REQUEST_READ_EXTERNAL_STORAGE)
                    getPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, BTS_Constants.REQUEST_WRITE_EXTERNAL_STORAGE)
                    userAgreement()
                }
            }
            BTS_Constants.REQUEST_READ_EXTERNAL_STORAGE -> {
                if (grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    getPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, BTS_Constants.REQUEST_WRITE_EXTERNAL_STORAGE)
                    getPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION, BTS_Constants.REQUEST_COURSE_LOCATION)
                    userAgreement()
                }
            }
            BTS_Constants.REQUEST_WRITE_EXTERNAL_STORAGE ->{
                if (grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    getPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE, BTS_Constants.REQUEST_READ_EXTERNAL_STORAGE)
                    getPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION, BTS_Constants.REQUEST_COURSE_LOCATION)
                    userAgreement()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mPairedDeviceArrayAdapter.clear()
            val startFunc = fun(){
                if (!mBtAdapter.isEnabled){
                    mBtAdapter.enable()
                    while (!mBtAdapter.isEnabled){
                        Thread.sleep(100)
                    }
                }
            }
            val endFunc = fun(){
                populatePairedList()
            }
            val task = SyncedTask(startFunc, endFunc)
            task.execute(null as Void?)
    }

    override fun onStop() {
        super.onStop()
        mBtAdapter.disable()
    }

    override fun onDestroy(){
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        menu.getItem(0).isEnabled = false
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.getItem(0).isEnabled = isConnected
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.sendOption -> {
                val fileSelector = FileSelectorDialog.newInstance()
                fileSelector.show(fragmentManager, BTS_Constants.FRAGMENT_TAG) //use newinstance here
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode){
            BTS_Constants.REQUEST_BT_ENABLE -> {
                if (resultCode==Activity.RESULT_OK){
                    val r = data?.getStringExtra(BTS_Constants.BLUETOOTH_AUTH_RESULT)
                    if (r == BTS_Constants.AUTH_NO) {
                        finish()
                    }else{
                        saveBluetoothAuth()
                        val startFunc = fun(){
                            if (!mBtAdapter.isEnabled){
                                mBtAdapter.enable()
                                while (!mBtAdapter.isEnabled){
                                    Thread.sleep(100)
                                }
                            }
                        }
                        val endFunc = fun(){
                            populatePairedList()
                        }
                        val task = SyncedTask(startFunc, endFunc)
                        task.execute(null as Void?)
                    }
                }
            }
        }
    }

    fun discover(){
        setTitle(R.string.title_scanning)
        mNewDeviceArrayAdapter.clear()
        if (mBtAdapter.isDiscovering){
            mBtAdapter.cancelDiscovery()
        }
        mBtAdapter.startDiscovery()
        Log.d(TAG, "discovery started")
    }

    fun populatePairedList() {
        mPairedDeviceArrayAdapter.clear()
        if (mBtAdapter.bondedDevices.size>0) mPairedLabel.visibility = View.VISIBLE
        for (dev in mBtAdapter.bondedDevices) {
            mPairedDeviceArrayAdapter.add("${dev.name ?: "unkown"}\n${dev.address}")
            mDevices.add(dev)
        }
    }

    fun saveBluetoothAuth(){
        val prefs = this.getSharedPreferences(TAG, android.content.Context.MODE_PRIVATE)
                .edit()
        prefs.putBoolean(BTS_Constants.BLUETOOTH_AUTH, true).apply()
    }

    fun userAgreement(){
            val prefs = this.getSharedPreferences(TAG, android.content.Context.MODE_PRIVATE)
            val bluetoothAuth = prefs.getBoolean(BTS_Constants.BLUETOOTH_AUTH, false)
            Log.d(TAG, bluetoothAuth.toString())
            if (!bluetoothAuth){
                startActivityForResult(Intent(this@DeviceListActivity, BluetoothAuthActivity::class.java), BTS_Constants.REQUEST_BT_ENABLE)
            }else{
                Log.d(TAG, "auth: $bluetoothAuth")
               val task = SyncedEnableTask()
                task.execute(null as Void?)
            }
    }

    fun setHighlighted(view:ListView, selector:String, color:Int=Color.WHITE){
        Log.d(TAG, view.adapter.count.toString())
        for (i in 0..view.adapter.count - 1){
            val v = view.getChildAt(i).findViewById(R.id.device_label_text) as TextView
            val text = v.text.toString()
            Log.d(TAG, "$text")
            val c = if (color==BTS_Constants.CYAN) Color.parseColor("#00FFFF") else Color.WHITE
            if (selector == text) v.setBackgroundColor(c)
        }
    }

    fun getProgress(l:Long, off:Int):Int{
        var x = off.toLong().div(l)
        return x.times(100).toInt()
    }

    val mReceiver = object:BroadcastReceiver(){
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action){
                BluetoothDevice.ACTION_FOUND -> {
                    mNewLabel.visibility = View.VISIBLE
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    mNewDeviceArrayAdapter.add("${device.name ?: "unkown"}\n${device.address}")
                    mDevices.add(device)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    setTitle(R.string.app_name)
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    BluetoothService.log("connection made")
                    isConnected = true
                    invalidateOptionsMenu()
                    val dev = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val selector = "${dev.name}\n${dev.address}"
                    setHighlighted(mNewListView, selector, BTS_Constants.CYAN)
                    setHighlighted(mPairedListView, selector, BTS_Constants.CYAN)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    BluetoothService.log("connection ended")
                    BluetoothService.disconnect()
                    isConnected = false
                    invalidateOptionsMenu()
                    val dev = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val selector = "${dev.name}\n${dev.address}"
                    setHighlighted(mNewListView, selector)
                    setHighlighted(mPairedListView, selector)
                }
            }
        }

    }

    val mTouchListener = object: AdapterView.OnItemClickListener{
        override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
            Log.d(TAG, "$isConnected")
            if (!isConnected) {
                val info = (view as TextView).text
                val name = info.substring(0, info.length - 17)
                val address = info.substring(info.length - 17)
                val adBuilder = AlertDialog.Builder(this@DeviceListActivity)
                        .setTitle("Connect to : $name")
                        .setNegativeButton("Cancel", DialogInterface.OnClickListener { dialog, _ ->
                            dialog.cancel()
                        })
                        .setPositiveButton("Yes", DialogInterface.OnClickListener { _, _ ->
                            mDevices.forEach {
                                if (it.address == address) {
                                    Log.d(TAG, "Attempting to connect to ${it.name}")
                                    BluetoothService.connect(it)
                                }
                            }
                        })

                adBuilder.create().show()
            }else{
                val name = (view as TextView).text.toString().let {
                    it.substring(0, it.length -17)
                }
                AlertDialog.Builder(this@DeviceListActivity)
                        .setTitle("Disconnect from : $name?")
                        .setNegativeButton("Cancel", DialogInterface.OnClickListener{ dialog, _ ->
                            dialog.cancel()
                        })
                        .setPositiveButton("Yes", DialogInterface.OnClickListener{_, _ ->
                            BluetoothService.disconnect()
                        })
                        .create().show()
            }
        }

    }

}