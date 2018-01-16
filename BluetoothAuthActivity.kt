package com.example.zemcd.fileblue

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button

class BluetoothAuthActivity : Activity() {

    val mResult = Intent()

    lateinit var acceptButton:Button
    lateinit var closeButton:Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_auth)
        setFinishOnTouchOutside(false)
        title = "Attention! read carefully:"

        val acceptButton = findViewById(R.id.authAcceptButton) as Button
        val closeButton = findViewById(R.id.close_button) as Button
        acceptButton.setOnClickListener {
            mResult.putExtra(BTS_Constants.BLUETOOTH_AUTH_RESULT, BTS_Constants.AUTH_YES)
            setResult(RESULT_OK, mResult)
            finish()
        }
        closeButton.setOnClickListener{
            mResult.putExtra(BTS_Constants.BLUETOOTH_AUTH_RESULT, BTS_Constants.AUTH_NO)
            setResult(RESULT_OK, mResult)
            finish()
        }

    }
}
