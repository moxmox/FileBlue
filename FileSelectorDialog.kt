package com.example.zemcd.fileblue

import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.app.NotificationManager
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.support.design.widget.Snackbar
import android.support.v7.app.NotificationCompat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import java.io.File
import kotlin.concurrent.thread

class FileSelectorDialog : DialogFragment(){

    val TAG = "FileSelectorDialog"

    lateinit var fileListView:ListView
    lateinit var fileListAdapter:ArrayAdapter<String>

    lateinit var hostView:View

    companion object {
        fun newInstance() : FileSelectorDialog{
            val fileSelector = FileSelectorDialog()
            return fileSelector
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        val v = LayoutInflater.from(activity).inflate(R.layout.file_selector_dialog, null, false)
        builder.setView(v)

        hostView = v.findViewById(R.id.dialog_layout)

        fileListAdapter = ArrayAdapter(activity, R.layout.file_item)
        fileListView = v.findViewById(R.id.directoryListView) as ListView
        fileListView.onItemClickListener = object:AdapterView.OnItemClickListener{
            override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val v = view as TextView
                if (v.text.endsWith(" - Dir")){
                    fileManager.cd(fileManager.trimFileName(v.text.toString()))
                    fileListAdapter.clear()
                    fileListAdapter.addAll(fileManager.ls())
                }else if (v.text.endsWith(" - File")){
                    val fileName = fileManager.trimFileName(v.text.toString())
                    Snackbar.make(hostView, "Send $fileName?", Snackbar.LENGTH_INDEFINITE)
                            .setAction(R.string.snackbar_confirm){
                                //this toast is filler and will be replaced with calls to BTS functions
                                Toast.makeText(hostView.context, "sending file", Toast.LENGTH_SHORT).show()
                                //BluetoothService.send(fileManager.select(fileName))
                                //DeviceListActivity.send(fileManager.select(fileName))
                                thread {
                                    val file = fileManager.select(fileName)
                                    val inStream = file.inputStream()
                                    val length = file.length()

                                    val b = ByteArray(1000000)

                                    var count = 0 //used to keep track of progress through file
                                    var off = 0
                                    var numRead = 0

                                    val notificationManager = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                    val notificationBuilder = NotificationCompat.Builder(activity)
                                    notificationBuilder
                                            .setContentTitle("File Transfer")
                                            .setSmallIcon(R.drawable.abc_btn_radio_material)
                                            .setContentText("Sending $fileName...")
                                            .setOngoing(true)
                                            .setProgress(length.toInt(), 0, false)
                                    notificationManager.notify(BTS_Constants.NOTIFICATION_ID, notificationBuilder.build())

                                    val header = "${BTS_Constants.START_MARKER}$fileName:$length:".toByteArray()
                                    BluetoothService.send(header, header.size.toLong(), header.size)

                                    while (count < length){
                                            try {
                                                numRead = inStream.read(b, off, 1000000)
                                                off+=numRead
                                                count+=numRead
                                                if (!(numRead>=0))break
                                                off = 0
                                            }catch (ae:ArrayIndexOutOfBoundsException) {
                                                BluetoothService.log("end of file reached", ae)
                                            }
                                        if (BluetoothService.send(b, length, count)){
                                            Log.d(TAG, "count: $count\nlength: ${length.toInt()}")
                                            notificationBuilder.setProgress(length.toInt(), count, false)
                                            notificationManager.notify(BTS_Constants.NOTIFICATION_ID, notificationBuilder.build())
                                        }
                                    }
                                    notificationBuilder.setProgress(0, 0, false)
                                            .setOngoing(false)
                                            .setContentText("Finished sending")
                                    notificationManager.notify(BTS_Constants.NOTIFICATION_ID, notificationBuilder.build())
                                }//end thread function
                            }.show()
                }
            }

        }
        fileListView.adapter = fileListAdapter
        fileListAdapter.addAll(fileManager.ls())


        builder.setTitle(R.string.file_choice)
        builder.setNegativeButton(R.string.dialog_cancel) { dialog, _ ->
            dialog?.cancel()
        }
        builder.setPositiveButton(R.string.dialog_up) { _, _ ->
            //THIS BUTTON CLICK IS HANDLED BY LISTENER BELOW!
        }
        //return builder.create()
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog -> (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (fileManager.current==fileManager.top){
                Toast.makeText(activity, "Cannot go up", Toast.LENGTH_SHORT).show()
            }else{
                fileManager.up()
                fileListAdapter.clear()
                fileListAdapter.addAll(fileManager.ls())
            }
        }//end click listener
        }//end onShowListener
        return dialog
    }

    object fileManager{
        val top = Environment.getExternalStorageDirectory()
        var current = top

        fun ls() : List<String>{
            return current.list()
                    .toList()
                    .filter {
                !it.startsWith(".")
            }.map {
                file ->
                if(File("$top/$file").isDirectory) "$file - Dir" else "$file - File"
            }
        }

        fun cd(dest:String){
            current = File("$current/$dest")
            Log.d(this.toString(), "$current")
        }

        fun up(){
            //current = current.parentFile
            current = File(current.toString().substringBeforeLast("/"))
            Log.d(this.toString(), "$current")
        }

        fun select(fileName:String) : File{
            return File("$current/$fileName")
        }

        fun trimFileName(displayed:String):String{
            return  if (displayed.contains(" - Dir")){
                displayed.removeSuffix(" - Dir")
            }else if (displayed.contains(" - File")){
                displayed.removeSuffix(" - File")
            }else{
                displayed
            }
        }
    }

}