package com.example.zemcd.fileblue

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Environment
import android.os.Looper
import android.util.Log
import java.io.*
import android.support.v7.app.NotificationCompat
import android.widget.Toast
import java.util.function.IntFunction

//class BluetoothService private constructor(){
object BluetoothService{
    val TAG = "BluetoothService"

    var state:Int = BTS_Constants.STATE_NONE
    var context:Context? = null

    //Threads
    private var mAcceptThread:AcceptThread? = null

    var mConnectedThread:ConnectedThread? = null
    var mConnectThread:ConnectThread? = null

    private var mAdapter:BluetoothAdapter

    init {
        mAdapter = BluetoothAdapter.getDefaultAdapter()
    }

    @Synchronized fun start(context: Context){
        this.context = context

        if (mAcceptThread==null){
            mAcceptThread = AcceptThread(mAdapter)
            mAcceptThread!!.start()
        }

        mConnectThread?.let {
            mConnectThread?.cancel()
            mConnectThread = null
        }

    }

    fun disconnect() = mConnectedThread?.cancel()

    @Synchronized fun connect(device:BluetoothDevice){
        Log.d(BTS_Constants.SERVICE_NAME, "Attemping connect to ${device.name}")

        if (state!=BTS_Constants.STATE_NONE || state!=BTS_Constants.STATE_CONNECTED){
            mConnectThread?.let {
                it.cancel()
                mConnectThread = null
            }

            mConnectedThread?.let {
                it.cancel()
                mConnectedThread = null
                BluetoothService.log("connect should be prevented when connection is already active!")
            }

            //start new connectThread
            mConnectThread = ConnectThread(device)
            mConnectThread?.start()
        }
    }

    fun log(message: String, error:Exception? = null) {
        error?.let {
            Log.e(BTS_Constants.SERVICE_NAME, message, it)
            return@log
        }
        Log.d(BTS_Constants.SERVICE_NAME, message)
    }

    @Synchronized fun send(bytes:ByteArray, fileLength:Long, progress:Int):Boolean{
        var synThread:ConnectedThread? = null
        synchronized(this@BluetoothService){
            synThread = mConnectedThread
        }

        var success:Boolean

        try {
            synThread?.write(bytes, progress, fileLength.toInt())
            success = true
        }catch (e:Exception){
            success = false
        }

        return success

    }

        fun cancelDiscovery() = mAdapter.cancelDiscovery()
}

class AcceptThread(val btAdapter:BluetoothAdapter):Thread(){
    var mmServerSocket:BluetoothServerSocket

    init {
        Log.d(BTS_Constants.SERVICE_NAME, "${BTS_Constants.THREAD_ACCEPT} started")
        try{
            mmServerSocket = btAdapter.listenUsingRfcommWithServiceRecord(BTS_Constants.SERVICE_NAME, BTS_Constants._UUID_SECURE)
        }catch(ioe:IOException){
            Log.e(BTS_Constants.SERVICE_NAME, "Error Listening for connection", ioe)
            throw BluetoothServiceException()
        }

        BluetoothService.state = BTS_Constants.STATE_LISTENING
    }

    override fun run() {
        Log.d(BTS_Constants.SERVICE_NAME, "Thread : ${BTS_Constants.THREAD_ACCEPT}")
        name = BTS_Constants.THREAD_ACCEPT

        while (true){
            /* this is old while condition, apperent faulty logic:
             *BluetoothService.state != BTS_Constants.STATE_CONNECTING
             */
            var socket:BluetoothSocket?

            try {
                socket = mmServerSocket.accept()
                if (socket.isConnected){
                    BluetoothService.state = BTS_Constants.STATE_CONNECTING
                    Log.d(BTS_Constants.SERVICE_NAME, "State : ${BluetoothService.state}")
                }
            }catch (ioe:IOException){
                Log.e(BTS_Constants.SERVICE_NAME, "Error accepting connection", ioe)
                throw BluetoothServiceException()
            }

            socket?.let {
                when(BluetoothService.state){
                    BTS_Constants.STATE_LISTENING -> {BluetoothService.log("socket is not connected after call to accept")}
                    BTS_Constants.STATE_CONNECTING -> {
                        BluetoothService.log("state connecting when reached")
                        BluetoothService.mConnectedThread = ConnectedThread(socket!!)
                        BluetoothService.mConnectedThread?.start()
                    }
                    BTS_Constants.STATE_CONNECTED,
                    BTS_Constants.STATE_NONE -> {
                        BluetoothService.log("Already connected")
                    }
                    else -> {
                        throw BluetoothServiceException()
                    }
                }
            }

        }//END ACCEPT LOOP
    }

    fun cancel(){
        Log.d(BTS_Constants.SERVICE_NAME, "${BTS_Constants.THREAD_ACCEPT} cancelled")
        mmServerSocket.close()
        BluetoothService.state = BTS_Constants.STATE_NONE
    }
}

class ConnectThread(val device:BluetoothDevice):Thread(){

    var socket:BluetoothSocket? = null

    init {
        try{
            socket = device.createRfcommSocketToServiceRecord(BTS_Constants._UUID_SECURE)
            BluetoothService.state = BTS_Constants.STATE_CONNECTING
        }catch (ioe:IOException){
            Log.e(BTS_Constants.SERVICE_NAME, "Socket create failed", ioe)
        }
    }

    override fun run() {
        Log.i(BTS_Constants.SERVICE_NAME, "Begin ${BTS_Constants.THREAD_CONNECT}")
        name = BTS_Constants.THREAD_CONNECT

        BluetoothService.cancelDiscovery()

        try {
            Log.i(BTS_Constants.SERVICE_NAME, "Connecting . . . ")
                socket?.connect()
        }catch (ioe:IOException){
            Log.e(BTS_Constants.SERVICE_NAME, "unable to connect", ioe)
            socket?.close()
            //connection failed
            return
        }

        Log.d(BTS_Constants.SERVICE_NAME, "Socket : $socket is connected")

        BluetoothService.state = BTS_Constants.STATE_CONNECTED

        BluetoothService.log("Connect Thread : ${BluetoothService.mConnectThread}")
        socket?.let {
            //ConnectedThread(socket!!).start()
            BluetoothService.mConnectedThread = ConnectedThread(it)
        }
        BluetoothService.mConnectedThread?.start()

    }

    fun cancel(){
        socket?.close()
    }

}

class ConnectedThread(val socket: BluetoothSocket) : Thread(){

    var fileName:String = ""
    var fileLength:Int = 0

    val inStream:InputStream
    val outStream:OutputStream
    val outBuffer:ByteArray
    var inBuffer:ByteArray
    var fOut:FileOutputStream? = null
    var bytes:Int = 0
    var active = true
    val notifyManager = BluetoothService.context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    var notifyBuilder:NotificationCompat.Builder? = null

    init {
        inStream = socket.inputStream
        outStream = socket.outputStream
        outBuffer = ByteArray(1000000)
        inBuffer = ByteArray(1024)
        notifyBuilder = NotificationCompat.Builder(BluetoothService.context)
        notifyBuilder?.setSmallIcon(R.drawable.abc_btn_radio_material)
                ?.setContentTitle("Downloading")
    }

    override fun run() {
        BluetoothService.log("$this waiting to read . . .")
        while (active){
            try {
                bytes += inStream.read(inBuffer)
                var header = String(inBuffer)
                if (header.startsWith(BTS_Constants.START_MARKER)){
                    this.fileName = header.substringAfter(BTS_Constants.START_MARKER).substringBefore(":")
                    this.fileLength = (header.substringAfter(":").substringBeforeLast(":")).toInt()
                    val path = "${Environment.DIRECTORY_DOWNLOADS}/$fileName"
                    val outFile = File(Environment.getExternalStoragePublicDirectory(path).toURI())
                    if (outFile.exists()){
                        BluetoothService.log("file already exists")
                    }else{
                        outFile.createNewFile()
                    }
                    fOut = outFile.outputStream()
                    inBuffer = ByteArray(1000000)
                    bytes = 0
                    notifyBuilder!!.setContentText("file: $fileName")
                            .setProgress(0, 0, true)
                            .setOngoing(true)
                            .setVibrate(LongArray(1){
                                10000L
                            })
                    notifyManager.notify(BTS_Constants.NOTIFICATION_ID, notifyBuilder!!.build())
                    BluetoothService.log("name = $fileName, length = $fileLength")
                }else if(bytes>=fileLength){
                    notifyBuilder!!.setProgress(0, 0, false)
                            .setContentText("Download complete")
                            .setOngoing(false)
                    notifyManager.notify(BTS_Constants.NOTIFICATION_ID, notifyBuilder!!.build())
                    //possibly save last bytes of file here
                }else{
                    BluetoothService.log("bytes: $bytes read: $inBuffer")
                    fOut?.write(inBuffer)
                }
                BluetoothService.log("read $bytes bytes: $inBuffer")
            }catch (ioe:IOException){
                BluetoothService.log("failed to read from $socket", ioe)
                cancel()
            }
        }
    }

    fun write(bytes:ByteArray, progress: Int, length:Int){
        BluetoothService.log("writing bytes from $this")
        outStream.write(bytes)
        outStream.flush()
        BluetoothService.log("bytes written")
    }

    fun cancel(){
        BluetoothService.log("closing socket, read may fail - this is expected")
        active = false
        socket.close()
    }
}

data class BluetoothServiceException(override val message:String="Invalid Bluetooth Service State"):Exception()