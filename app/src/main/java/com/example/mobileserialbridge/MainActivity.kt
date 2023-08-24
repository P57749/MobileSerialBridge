package com.example.mobileserialbridge

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var usbManager: UsbManager
    private var usbDevice: UsbDevice? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var usbEndpoint: UsbEndpoint? = null

    private lateinit var editText: EditText
    private lateinit var sendButton: Button
    private lateinit var receivedText: TextView

    private val ACTION_USB_PERMISSION = "com.example.mobileserialbridge.USB_PERMISSION"

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (ACTION_USB_PERMISSION == intent?.action) {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (granted) {
                    setupUsbConnection()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editText = findViewById(R.id.editText)
        sendButton = findViewById(R.id.sendButton)
        receivedText = findViewById(R.id.receivedText)

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        sendButton.setOnClickListener {
            val dataToSend = editText.text.toString()
            sendData(dataToSend)
        }

        requestUsbPermission()

        val deviceList = usbManager.deviceList
        if (deviceList.isNotEmpty()) {
            usbDevice = deviceList.values.first()
            setupUsbConnection()
        }
    }

    private fun UsbDevice.getEndpoint(i: Int): UsbEndpoint? {
    // Ipmplementar esta fucnion para recibir los endpoint deseados basados en el indice 1
    return getInterface(0)?.getEndpoint(i)
}

    private fun setupUsbConnection() {
        usbDevice?.let {
            usbConnection = usbManager.openDevice(it)
            usbEndpoint = it.getEndpoint(0) // Adjust this based on your configuration
        }
    }

    private fun requestUsbPermission() {
        val permissionIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_USB_PERMISSION),
            0
        )
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(usbReceiver, filter)

        val deviceList = usbManager.deviceList
        if (deviceList.isNotEmpty()) {
            val usbDevice = deviceList.values.first()
            if (!usbManager.hasPermission(usbDevice)) {
                usbManager.requestPermission(usbDevice, permissionIntent)
            }
        }
    }

    private fun sendData(data: String) {
        usbConnection?.apply {
            val sendData = data.toByteArray()
            usbEndpoint?.let {
                val bytesWritten = bulkTransfer(it, sendData, sendData.size, 100)
                if (bytesWritten >= 0) {
                    val currentConversation = receivedText.text.toString()
                    val newMessage = "You: $data"
                    val newConversation = "$currentConversation\n$newMessage"
                    receivedText.text = newConversation
                } else {
                    // Handle error
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        usbConnection?.close()
    }
}
