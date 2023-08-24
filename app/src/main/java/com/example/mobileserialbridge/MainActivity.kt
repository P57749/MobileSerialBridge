package com.example.mobileserialbridge

import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


import com.google.android.things.pio.UartDevice
import com.google.android.things.pio.UartDevice.Companion.CONTROL_NONE
import com.google.android.things.pio.UartDevice.Companion.DATA_BITS_8
import com.google.android.things.pio.UartDevice.Companion.FLOW_CONTROL_NONE
import com.google.android.things.pio.UartDevice.Companion.PARITY_NONE
import com.google.android.things.pio.UartDevice.Companion.STOP_BITS_1
import kotlinx.coroutines.withContext


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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var usbManager: UsbManager
    private var usbDevice: UsbDevice? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var usbEndpoint: UsbEndpoint? = null

    private lateinit var editText: EditText
    private lateinit var sendButton: Button
    private lateinit var receivedText: TextView

    private var serialDevice: UsbSerialDevice? = null
    private var serialConnection: UsbSerialConnection? = null

    private val ACTION_USB_PERMISSION = "com.example.mobileserialbridge.USB_PERMISSION"

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (ACTION_USB_PERMISSION == intent?.action) {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (granted) {
                    setupUsbConnection(usbDevice!!)
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
        connectToAvailableDevice()
    }

    private fun setupUsbConnection(usbDevice: UsbDevice) {
        this.usbDevice?.let {
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
        val newMessage = "You: $data"
        addMessageToConversation(newMessage)
    }

    private fun connectToAvailableDevice() {
        val deviceList = usbManager.deviceList
        if (deviceList.isNotEmpty()) {
            val usbDevice = deviceList.values.first()
            serialConnection = UsbSerialConnection(usbManager, usbDevice)
            serialDevice = serialConnection?.setupSerialConnection()
        } else {
            // No available USB devices
        }
    }

    private fun addMessageToConversation(message: String) {
        val textView = TextView(this)
        textView.text = message
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.setMargins(16, 16, 16, 16)
        textView.layoutParams = layoutParams
        val chatContainer = findViewById<LinearLayout>(R.id.chatContainer)
        chatContainer.addView(textView)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        usbConnection?.close()
    }
}

inner class UsbSerialConnection(private val usbManager: UsbManager, private val usbDevice: UsbDevice) {

    private var serialDevice: UsbSerialDevice? = null

    fun setupSerialConnection(): UsbSerialDevice? {
        val connection = usbManager.openDevice(usbDevice)
        val driver = UsbSerialProber.getDefaultProber().probeDevice(usbDevice)
        if (connection != null && driver != null) {
            serialDevice = driver.open(connection)
            configureSerialDevice()
        }
        return serialDevice
    }

    private fun configureSerialDevice() {
        serialDevice?.apply {
            setBaudRate(115200) // Set your desired baud rate
            setDataBits(UsbSerialInterface.DATA_BITS_8)
            setStopBits(UsbSerialInterface.STOP_BITS_1)
            setParity(UsbSerialInterface.PARITY_NONE)
            // Set other UART configuration settings here using methods provided by your library
        }
    }


    fun sendData(data: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val dataToSend = data.toByteArray()
            serialDevice?.write(dataToSend)
        }
    }

    fun close() {
        serialDevice?.close()
    }

    private fun setBaudRate(baudRate: Int) {
        serialDevice?.apply {
            val serialPort = this
            val uartConfig = UartConfig(baudRate, UartConfig.DATA_BITS_8, UartConfig.STOP_BITS_1, UartConfig.PARITY_NONE)
            serialPort.uartDevice.apply {
                uartConfig.apply {
                    //setBaudrate(baudRate)
                    setDataSize(dataBits)
                    setParity(parity)
                    setStopBits(stopBits)
                    setFlowControl(flowControl)
                    setRts(rts)
                    setDtr(dtr)
                }
            }
        }
    }

    private fun setDataBits(dataBits: Int) {
        serialDevice?.uartDevice?.setDataSize(dataBits)
    }

    private fun setStopBits(stopBits: Int) {
        serialDevice?.uartDevice?.setStopBits(stopBits)
    }

    private fun setParity(parity: Int) {
        serialDevice?.uartDevice?.setParity(parity)
    }

    // ... Other methods ...

}

// ... Other methods ...

}