package com.example.xfoodz.scancode;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.media.Image;
import android.media.ImageReader;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.speech.tts.TextToSpeech;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.xfoodz.scancode.MVVM.VM.NPNHomeViewModel;
import com.example.xfoodz.scancode.MVVM.View.NPNHomeView;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.SpiDevice;
import com.google.android.things.pio.UartDevice;
import com.google.android.things.pio.UartDeviceCallback;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.common.HybridBinarizer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import androidmads.library.qrgenearator.QRGContents;
import androidmads.library.qrgenearator.QRGEncoder;


/**
 * Skeleton of an Android Things activity.
 * <p>
 * Android Things peripheral APIs are accessible through the class
 * PeripheralManagerService. For example, the snippet below will open a GPIO pin and
 * set it to HIGH:
 * <p>
 * <pre>{@code
 * PeripheralManagerService service = new PeripheralManagerService();
 * mLedGpio = service.openGpio("BCM6");
 * mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
 * mLedGpio.setValue(true);
 * }</pre>
 * <p>
 * For more complex peripherals, look for an existing user-space driver, or implement one if none
 * is available.
 *
 * @see <a href="https://github.com/androidthings/contrib-drivers#readme">https://github.com/androidthings/contrib-drivers#readme</a>
 */

public class MainActivity extends Activity implements NPNHomeView, TextToSpeech.OnInitListener {
    String TAG = "ScanQRCode";
    private Camera mCamera;
    private Handler mCameraHandler = new Handler();
    private ImageView qrImage;
    private boolean standby = false;
    private Context context = this;
    private TextView txtIPAddress;
    private ImageView imgWifi;

    private TextView time, date;
    private WifiManager wifi;
    private int level = 0;
    private ImageButton back;
    private Handler initTimeAndWifi = new Handler();
    private int idleCount = 0;
    private Handler mIdle = new Handler();
    private boolean idle = false;

    private boolean isDialog = false;

    private int DATA_CHECKING = 0;
    private TextToSpeech niceTTS;

    //GPIO Configuration Parameters
    private static final String LED_PIN_NAME = "BCM26"; // GPIO port wired to the LED
    private Gpio mLedGpio;

    //SPI Configuration Parameters
    private static final String SPI_DEVICE_NAME = "SPI0.1";
    private SpiDevice mSPIDevice;
    private static final String CS_PIN_NAME = "BCM12"; // GPIO port wired to the LED
    private Gpio mCS;


    // UART Configuration Parameters
    private static final int BAUD_RATE = 115200;
    private static final int DATA_BITS = 8;
    private static final int STOP_BITS = 1;
    private UartDevice mUartDevice;

    byte[] test_data = new byte[]{0,(byte)0x8b,0,0};


    private String DOOR_OPEN = "1";
    private String DOOR_CLOSE = "0";


    public enum DOOR_STATE{
        NONE, WAIT_DOOR_OPEN, WAIT_DOOR_CLOSE, DOOR_OPENED, DOOR_CLOSED
    }
    DOOR_STATE door_state = DOOR_STATE.NONE;
    private int door_timer = 0;
    private int TIME_OUT_DOOR_OPEN = 3;

    private static final int CHUNK_SIZE = 512;

    NPNHomeViewModel mHomeViewModel; //Request server object
    Timer mBlinkyTimer;             //Timer

    private boolean isAllowProcess = true;

    private String link = "http://f67bda42.ngrok.io/api/android/android?code=";

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //do they have the data
        if (requestCode == DATA_CHECKING) {
            //yep - go ahead and instantiate
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS)
                niceTTS = new TextToSpeech(this, this);
                //no data, prompt to install it
            else {
                Intent promptInstall = new Intent();
                promptInstall.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(promptInstall);
            }
        }
    }

    public void onInit(int initStatus) {
        if (initStatus == TextToSpeech.SUCCESS) {
            niceTTS.setLanguage(Locale.forLanguageTag("VI"));
        }
    }

    private String currentDoor = "";
    @Override
    public void onSuccessUpdateServer(String message) {
        Log.d(TAG, "Request server is successful " + message);
        //message = "1";
        if(message.equals("0")) {
            Log.d(TAG, "Wrong code");
            String speakWords = "Mã không hợp lệ";
            niceTTS.speak(speakWords, TextToSpeech.QUEUE_FLUSH, null);
            final Dialog dialog = new Dialog(context);
            dialog.setContentView(R.layout.dialog);
            dialog.setCancelable(true);
            Button back = dialog.findViewById(R.id.buttonBack);
            TextView textView = dialog.findViewById(R.id.textBox);

            textView.setText("Invalid QR Code");
            dialog.setTitle("");
            dialog.show();
            back.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dialog.cancel();
                    standby = false;
                    isDialog = false;
                    return;
                }
            });
        }
        else{
            final Dialog dialog = new Dialog(context);
            dialog.setContentView(R.layout.dialog);
            dialog.setCancelable(true);
            Button back = dialog.findViewById(R.id.buttonBack);
            TextView textView = dialog.findViewById(R.id.textBox);

            textView.setText("Successful");
            dialog.setTitle("");
            dialog.show();

            writeUartData(message);
            String speakWords = "Xin vui lòng đến ô số " + message;
            niceTTS.speak(speakWords, TextToSpeech.QUEUE_FLUSH, null);
            door_state = DOOR_STATE.WAIT_DOOR_OPEN;
            door_timer = TIME_OUT_DOOR_OPEN;
            currentDoor = message;
            readStatus(currentDoor);

            back.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dialog.cancel();
                    standby = false;
                    isDialog = false;
                    return;
                }
            });
        }
    }

    public void talkToMe(String sentence) {
        String speakWords = sentence;
        niceTTS.speak(speakWords, TextToSpeech.QUEUE_FLUSH, null);
    }

    @Override
    public void onErrorUpdateServer(String message) {
        //txtConsole.setText("Request server is fail");
        Log.d(TAG, "Request server is fail");
        String speakWords = "Không có kết nối đến máy chủ";
        niceTTS.speak(speakWords, TextToSpeech.QUEUE_FLUSH, null);
        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.dialog);
        dialog.setCancelable(true);
        Button back = dialog.findViewById(R.id.buttonBack);
        TextView textView = dialog.findViewById(R.id.textBox);

        textView.setText("CAN'T REACH THE SERVER!");
        dialog.setTitle("");
        dialog.show();
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.cancel();
                standby = false;
                isDialog = false;
                return;
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "Doorbell Activity created.");

        // We need permission to access the camera
        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // A problem occurred auto-granting the permission
            Log.e(TAG, "No permission");
            return;
        }

        mCamera = Camera.getInstance();
        mCamera.initializeCamera(this, mCameraHandler, mOnImageAvailableListener);

        mCameraHandler.post(mCameraRunnable);

        qrImage = findViewById(R.id.qrImage);

        imgWifi = findViewById(R.id.imgWifi);
        txtIPAddress = findViewById(R.id.txtIPAddress);

        time = findViewById(R.id.time);
        date = findViewById(R.id.date);
        back = findViewById(R.id.back);
        initTimeAndWifi.post(initTimeAndWifiRunnable);
        mIdle.post(mIdleRunnable);

        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent LaunchIntent = context.getPackageManager().getLaunchIntentForPackage("com.example.xfoodz.home");
                if(LaunchIntent != null) {
                    context.startActivity(LaunchIntent);
                    finish();
                    System.exit(0);
                }
            }
        });

        mHomeViewModel = new NPNHomeViewModel();
        mHomeViewModel.attach(this, this);

        initGPIO();
        initUart();
        initSPI();
        setupBlinkyTimer();
        //create an Intent
        Intent checkData = new Intent();
        //set it up to check for tts data
        checkData.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        //start it so that it returns the result
        startActivityForResult(checkData, DATA_CHECKING);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCamera.shutDown();
        // Attempt to close the UART device
        try {
            closeUart();
            mUartDevice.unregisterUartDeviceCallback(mCallback);
            closeSPI();
        } catch (IOException e) {
            Log.e(TAG, "Error closing UART device:", e);
        }
    }

    private ImageReader.OnImageAvailableListener mOnImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = reader.acquireLatestImage();
                    // get image bytes
                    ByteBuffer imageBuf = image.getPlanes()[0].getBuffer();
                    final byte[] imageBytes = new byte[imageBuf.remaining()];
                    imageBuf.get(imageBytes);
                    image.close();

                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                    int w = bitmap.getWidth();
                    int h = bitmap.getHeight();
                    if(w > 914) w = 914;
                    if(h > 400) h = 400;
                    Bitmap bitmap1 = Bitmap.createBitmap(bitmap, 0, 0, w, h);
                    qrImage.setImageBitmap(bitmap1);
                    final String decoded=scanQRImage(bitmap1);
                    Log.i("QrTest", "Decoded string="+decoded);
                    if(decoded != null) {
                        standby = true;
                        if(!isDialog){
                            isDialog = true;
                            isAllowProcess = false;
                            String url = link + decoded;
                            mHomeViewModel.updateToServer(url);
                            door_state = DOOR_STATE.DOOR_CLOSED;
                        }
                    }
                }
            };

    public static String scanQRImage(Bitmap bMap) {
        String contents = null;

        int[] intArray = new int[bMap.getWidth()*bMap.getHeight()];
        //copy pixel data from the Bitmap into the 'intArray' array
        bMap.getPixels(intArray, 0, bMap.getWidth(), 0, 0, bMap.getWidth(), bMap.getHeight());

        LuminanceSource source = new RGBLuminanceSource(bMap.getWidth(), bMap.getHeight(), intArray);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        Reader reader = new MultiFormatReader();
        try {
            Result result = reader.decode(bitmap);
            contents = result.getText();
        }
        catch (Exception e) {
            Log.e("QrTest", "Error decoding barcode", e);
        }
        return contents;
    }

    private Runnable mCameraRunnable = new Runnable() {
        @Override
        public void run() {
            if(!standby) mCamera.takePicture();
            mCameraHandler.post(mCameraRunnable);
        }
    };

    private Runnable initTimeAndWifiRunnable = new Runnable() {
        @Override
        public void run() {
            wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            int numberOfLevels = 5;
            WifiInfo wifiInfo = wifi.getConnectionInfo();
            level = WifiManager.calculateSignalLevel(wifiInfo.getRssi(), numberOfLevels);
            if(wifi.isWifiEnabled() == false) level = -1;
            if(isEthernetConnected()) level = -2;
            switch(level){
                case 0:
                    imgWifi.setImageResource(R.drawable.ic_signal_wifi_0_bar_black_48dp);
                    break;
                case 1:
                    imgWifi.setImageResource(R.drawable.ic_signal_wifi_1_bar_black_48dp);
                    break;
                case 2:
                    imgWifi.setImageResource(R.drawable.ic_signal_wifi_2_bar_black_48dp);
                    break;
                case 3:
                    imgWifi.setImageResource(R.drawable.ic_signal_wifi_3_bar_black_48dp);
                    break;
                case 4:
                    imgWifi.setImageResource(R.drawable.ic_signal_wifi_4_bar_black_48dp);
                    break;
                case -1:
                    imgWifi.setImageResource(R.drawable.ic_signal_wifi_off_bar_black_48dp);
                    break;
                case -2:
                    imgWifi.setImageResource(R.drawable.ic_computer_black_24dp);
                    break;
            }
            txtIPAddress = findViewById(R.id.txtIPAddress);
            if (wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {
                int ipAddress = wifiInfo.getIpAddress();
                String ipString = Formatter.formatIpAddress(ipAddress);
                txtIPAddress.setText(ipString);
            } else txtIPAddress.setText("No connection");

            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
            sdf.setTimeZone(TimeZone.getTimeZone("GMT+7"));
            SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
            date.setText(format.format(new Date()));
            format = new SimpleDateFormat("hh:mm");
            time.setText(format.format(new Date()));
        }
    };

    private Runnable mIdleRunnable = new Runnable() {
        @Override
        public void run() {
            if(idleCount == 30) {
                idle = true;
                idleCount = 0;
                Intent LaunchIntent = context.getPackageManager().getLaunchIntentForPackage("com.example.xfoodz.home");
                if(LaunchIntent != null) {
                    context.startActivity(LaunchIntent);
                    finish();
                    System.exit(0);
                }
            }
            else {
                if(standby == true || isDialog == true) idleCount = 0;
                else idleCount++;
            }
            if(!idle) mIdle.postDelayed(mIdleRunnable, 1000);
        }
    };

    private Boolean isNetworkAvailable() {
        ConnectivityManager cm
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting();
    }

    public Boolean isEthernetConnected(){
        if(isNetworkAvailable()){
            ConnectivityManager cm
                    = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            return (cm.getActiveNetworkInfo().getType() == ConnectivityManager.TYPE_ETHERNET);
        }
        return false;
    }

    private int counterWifi = 0;
    private void setupBlinkyTimer()
    {
        mBlinkyTimer = new Timer();
        TimerTask blinkyTask = new TimerTask() {
            @Override
            public void run() {
                counterWifi++;

                if(counterWifi >= 5) {
                    counterWifi = 0;
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                            int numberOfLevels = 5;
                            WifiInfo wifiInfo = wifi.getConnectionInfo();
                            level = WifiManager.calculateSignalLevel(wifiInfo.getRssi(), numberOfLevels);
                            if(wifi.isWifiEnabled() == false) level = -1;
                            switch(level){
                                case 0:
                                    imgWifi.setImageResource(R.drawable.ic_signal_wifi_0_bar_black_48dp);
                                    break;
                                case 1:
                                    imgWifi.setImageResource(R.drawable.ic_signal_wifi_1_bar_black_48dp);
                                    break;
                                case 2:
                                    imgWifi.setImageResource(R.drawable.ic_signal_wifi_2_bar_black_48dp);
                                    break;
                                case 3:
                                    imgWifi.setImageResource(R.drawable.ic_signal_wifi_3_bar_black_48dp);
                                    break;
                                case 4:
                                    imgWifi.setImageResource(R.drawable.ic_signal_wifi_4_bar_black_48dp);
                                    break;
                                case -1:
                                    imgWifi.setImageResource(R.drawable.ic_signal_wifi_off_bar_black_48dp);
                                    break;
                            }
                            txtIPAddress = findViewById(R.id.txtIPAddress);
                            if (wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {
                                int ipAddress = wifiInfo.getIpAddress();
                                String ipString = Formatter.formatIpAddress(ipAddress);
                                txtIPAddress.setText(ipString);
                            } else txtIPAddress.setText("No connection");

                            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
                            sdf.setTimeZone(TimeZone.getTimeZone("GMT+7"));
                            SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
                            date.setText(format.format(new Date()));
                            format = new SimpleDateFormat("hh:mm");
                            time.setText(format.format(new Date()));

                        }
                    });

                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                mLedGpio.setValue(!mLedGpio.getValue());

                            } catch (Throwable t) {
                                Log.d(TAG, "Error in Blinky LED " + t.getMessage());
                            }
                        }
                    });
                }
                //readStatus("1");
                switch (door_state){
                    case NONE:
                        if(door_timer > 0)
                        {
                            door_timer--;
                            if(door_timer == 0){
                                MainActivity.this.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
//                                        visibleAllControls(false);
                                        isAllowProcess = true;
                                    }
                                });

                            }
                        }
                        break;
                    case WAIT_DOOR_OPEN:
                        door_timer--;
                        if(door_status.equals(DOOR_OPEN) == true){
                            door_state = DOOR_STATE.DOOR_OPENED;
                        }else {
                            readStatus(currentDoor);
                        }
                        if(door_timer == 0)
                        {
                            Log.d("NPNIoTs", "Open again the door: " + currentDoor);
                            writeUartData(currentDoor);
                            door_timer = 3;
                        }
                        break;
                    case DOOR_OPENED:
                        door_timer = 10;
                        readStatus(currentDoor);
                        door_state = DOOR_STATE.WAIT_DOOR_CLOSE;
                        break;
                    case WAIT_DOOR_CLOSE:
                        door_timer--;
                        readStatus(currentDoor);
                        if(door_status.equals(DOOR_CLOSE)){
                            door_state = DOOR_STATE.DOOR_CLOSED;
                        }
                        if(door_timer <= 0)
                        {
                            talkToMe("Xin vui lòng đóng cửa số " + currentDoor);
                            door_timer = 5;
                        }
                        break;
                    case DOOR_CLOSED:
                        talkToMe("Xin cám ơn quý khách");
                        door_state = DOOR_STATE.NONE;
                        door_timer = 5;
                        break;
                    default:
                        break;
                }
            }
        };
        mBlinkyTimer.schedule(blinkyTask, 5000, 1000);
    }

    public void writeUartData(String message) {
        try {
            byte[] buffer = {'W',' ',' '};
            buffer[2] =  (byte)(Integer.parseInt(message));
            int count = mUartDevice.write(buffer, buffer.length);
            Log.d(TAG, "Send: "   + buffer[2]);
        }catch (IOException e)
        {
            Log.d(TAG, "Error on UART");
        }
    }

    public void readStatus(String ID)
    {
        try {
            byte[] buffer = {'R',' ',' '};
            buffer[2] =  (byte)(Integer.parseInt(ID));
            int count = mUartDevice.write(buffer, buffer.length);
            //Log.d(TAG, "Wrote " + count + " bytes to peripheral  "  + buffer[2]);
        }catch (IOException e)
        {
            Log.d(TAG, "Error on UART");
        }
    }


    private void initSPI()
    {
        PeripheralManager manager = PeripheralManager.getInstance();
        List<String> deviceList = manager.getSpiBusList();
        if(deviceList.isEmpty())
        {
            Log.d(TAG,"No SPI bus is not available");
        }
        else
        {
            Log.d(TAG,"SPI bus available: " + deviceList);
            //check if SPI_DEVICE_NAME is in list
            try {
                mSPIDevice = manager.openSpiDevice(SPI_DEVICE_NAME);

                mSPIDevice.setMode(SpiDevice.MODE1);
                mSPIDevice.setFrequency(1000000);
                mSPIDevice.setBitsPerWord(8);
                mSPIDevice.setBitJustification(SpiDevice.BIT_JUSTIFICATION_MSB_FIRST);


                Log.d(TAG,"SPI: OK... ");


            }catch (IOException e)
            {
                Log.d(TAG,"Open SPI bus fail... ");
            }
        }
    }



    private void sendCommand(SpiDevice device, byte[] buffer) throws  IOException{


        mCS.setValue(false);
        for(int i = 0; i < 100; i++) {}

        //send data to slave
        device.write(buffer, buffer.length);


        //read the response
        byte[] response = new byte[2];
        device.read(response, response.length);


        for(int i = 0; i< 2; i++) {

            Log.d(TAG, "Response byte " + Integer.toString(i) + " is: " + response[i]);
        }
        mCS.setValue(true);
        for(int i = 0; i < 100; i++){}

        double value = (double)(response[0] * 256 + response[1]);
        double adc = value * 6.144/32768;


    }

    private void initGPIO()
    {
        PeripheralManager manager = PeripheralManager.getInstance();
        try {
            mLedGpio = manager.openGpio(LED_PIN_NAME);
            // Step 2. Configure as an output.
            mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);

            mCS = manager.openGpio(CS_PIN_NAME);
            mCS.setDirection(Gpio.DIRECTION_OUT_INITIALLY_HIGH);


        } catch (IOException e) {
            Log.d(TAG, "Error on PeripheralIO API");
        }
    }

    private void initUart()
    {
        try {
            openUart("UART0", BAUD_RATE);
        }catch (IOException e) {
            Log.d(TAG, "Error on UART API");
        }
    }
    /**
     * Callback invoked when UART receives new incoming data.
     */
    private String door_status = "";
    private UartDeviceCallback mCallback = new UartDeviceCallback() {
        @Override
        public boolean onUartDeviceDataAvailable(UartDevice uart) {
            //read data from Rx buffer
            try {
                byte[] buffer = new byte[CHUNK_SIZE];
                int noBytes = -1;
                while ((noBytes = mUartDevice.read(buffer, buffer.length)) > 0) {
                    Log.d(TAG,"Number of bytes: " + Integer.toString(noBytes));

                    String str = new String(buffer,0,noBytes, "UTF-8");

                    Log.d(TAG,"Buffer is: " + str);
                    door_status = str;

                }
            } catch (IOException e) {
                Log.w(TAG, "Unable to transfer data over UART", e);
            }
            return true;
        }

        @Override
        public void onUartDeviceError(UartDevice uart, int error) {
            Log.w(TAG, uart + ": Error event " + error);
        }
    };

    private void openUart(String name, int baudRate) throws IOException {
        mUartDevice = PeripheralManager.getInstance().openUartDevice(name);
        // Configure the UART
        mUartDevice.setBaudrate(baudRate);
        mUartDevice.setDataSize(DATA_BITS);
        mUartDevice.setParity(UartDevice.PARITY_NONE);
        mUartDevice.setStopBits(STOP_BITS);
        mUartDevice.registerUartDeviceCallback(mCallback);
    }

    private void closeUart() throws IOException {
        if (mUartDevice != null) {
            mUartDevice.unregisterUartDeviceCallback(mCallback);
            try {
                mUartDevice.close();
            } finally {
                mUartDevice = null;
            }
        }
    }

    private void closeSPI() throws IOException {
        if(mSPIDevice != null)
        {
            try {
                mSPIDevice.close();
            }finally {
                mSPIDevice = null;
            }

        }
    }
}