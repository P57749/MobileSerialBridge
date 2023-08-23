package com.example.mobileserialbridge

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private fun UsbDevice.getEndpoint(i: Int): UsbEndpoint? {
    // Ipmplementar esta fucnion para recibir los endpoint deseados basados en el indice 1
    return getInterface(0)?.getEndpoint(i)
}


class MainActivity : AppCompatActivity() {


    private lateinit var editText: EditText
    private lateinit var sendButton: Button
    private lateinit var receivedText: TextView
    private lateinit var usbManager: UsbManager
    private var usbDevice: UsbDevice? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var usbEndpoint: UsbEndpoint? = null

    private val ACTION_USB_PERMISSION = UsbManager.ACTION_USB_DEVICE_ATTACHED
    private val PERMISSION_REQUEST_CODE = 1
    //val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editText = findViewById(R.id.editText)
        sendButton = findViewById(R.id.sendButton)
        receivedText = findViewById(R.id.receivedText)

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList: Map<String, UsbDevice> = usbManager.deviceList


        sendButton.setOnClickListener {
            val dataToSend = editText.text.toString()
            sendData(dataToSend)
        }

        requestUsbPermission()
        registerReceiver(usbReceiver, IntentFilter(ACTION_USB_PERMISSION))
        //UsbManager.ACTION_USB_PERMISSION
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (ACTION_USB_PERMISSION == intent?.action) {
                synchronized(this) {
                    val usbDevice = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        usbDevice?.let {
                            setupUsbConnection(usbDevice)
                        }
                    }
                }
            }
        }
    }

    private fun setupUsbConnection(device: UsbDevice) {
        usbConnection = usbManager.openDevice(device)
        usbEndpoint = device.getEndpoint(0) // Adjust this based on your Arduino configuration
    }






    private fun requestUsbPermission() {
        //UsbManager.ACTION_USB_PERMISSION
        val usbPermissionAction = UsbManager.ACTION_USB_DEVICE_ATTACHED
        if (ContextCompat.checkSelfPermission(this, usbPermissionAction) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(usbPermissionAction), PERMISSION_REQUEST_CODE)
        }
    }

/////////////////////
private fun sendData(data: String) {
    // Check for permission to access the USB device
    if (usbDevice != null && usbManager.hasPermission(usbDevice)) {
        usbConnection?.apply {
            val sendData = data.toByteArray()
            usbEndpoint?.let {
                val bytesWritten = bulkTransfer(it, sendData, sendData.size, 100)
                if (bytesWritten >= 0) {
                    // Data sent successfully, update the conversation
                    val currentConversation = receivedText.text.toString()
                    val newMessage = "You: $data"
                    val newConversation = "$currentConversation\n$newMessage"
                    receivedText.text = newConversation
                } else {
                    // Error sending data
                }
            }
        }
    }
}


    private fun receiveData() {
        val buffer = ByteArray(256) // Adjust the buffer size as needed
        usbConnection?.let { connection ->
            usbEndpoint?.let { endpoint ->
                val bytesRead = connection.bulkTransfer(endpoint, buffer, buffer.size, 100)
                if (bytesRead > 0) {
                    val receivedString = buffer.sliceArray(0 until bytesRead).toString(Charsets.UTF_8)
                    runOnUiThread {
                        updateReceivedText("ESP32: $receivedString")
                    }
                }
            }
        }
    }



    override fun onResume() {
        super.onResume()

        // Encuentra y soliicita un permiso para el usb
        val deviceList = usbManager.deviceList
        for (device in deviceList.values) {
            val connection = usbManager.openDevice(device)
            val endpoint =
                device.getEndpoint(0) // Adjust this based on your Arduino/ESP32 configuration

            val buffer = ByteArray(32)
            val bytesRead = connection?.bulkTransfer(endpoint, buffer, buffer.size, 100)
            if (bytesRead != null) {
                if (bytesRead > 0) {
                    val identification = buffer.sliceArray(0 until bytesRead).toString(Charsets.UTF_8)

                    when (identification) {

                        "ESP32_CONNECTED" -> {
                            setupESP32Connection(device)
                            break
                        }
                    }
                    // Handle the identification here
                } else {
                    // No data read, handle this case accordingly
                }
            }


        }
    }


    private fun setupESP32Connection(device: UsbDevice) {
        usbDevice = device
        usbConnection = usbManager.openDevice(device)
        usbEndpoint = device.getEndpoint(0) // Adjust this based on your ESP32 configuration

        // Perform any initialization or setup specific to ESP32 communication here

        // Start a separate thread for receiving data from the ESP32
        Thread {
            val buffer = ByteArray(256)
            while (true) {
                usbConnection?.let { connection ->
                    val bytesRead = connection.bulkTransfer(usbEndpoint, buffer, buffer.size, 100)
                    if (bytesRead > 0) {
                        val receivedString =
                            buffer.sliceArray(0 until bytesRead).toString(Charsets.UTF_8)
                        runOnUiThread {
                            updateReceivedText("ESP32: $receivedString")
                        }
                    }
                }
            }
        }.start()
    }

    private fun updateReceivedText(message: String) {
        val currentConversation = receivedText.text.toString()
        val newConversation = "$currentConversation\n$message"
        receivedText.text = newConversation
    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        usbConnection?.close()
    }
}

