package com.embedhome.btlamp.wake;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class BluetoothWake {

    private static BluetoothWake mInstance = new BluetoothWake();

    private BluetoothAdapter btAdapter;
    private OnEventListener btListener;
    private volatile int btStatus;
    private int btWakeAddr;

    private btConnectionThread btConnectionTask;
    private btCheckStatusThread btCheckStatusTask;

    // SPP UUID сервиса
    private static final String SPP_UUID = "00001101-0000-1000-8000-00805f9b34fb";

    private static final int BT_STATUS_NOT_AVAILABLE = 0;
    private static final int BT_STATUS_DISABLE = 1;
    private static final int BT_STATUS_ENABLE = 2;
    private static final int BT_STATUS_CONNECT = 3;

    private static final int BT_EVENT_DICOVERING_START = 0;
    private static final int BT_EVENT_DISCOVERING_CANCEL = 1;
    private static final int BT_EVENT_CONNECT_OK = 2;
    private static final int BT_EVENT_CONNECT_ERR = 3;
    private static final int BT_EVENT_STREAM_ERR = 4;
    private static final int BT_EVENT_IO_STREAM_ERR = 5;

    private static final int WAKE_EVENT_RX_PACKAGE = 6;


    private static final int WAKE_FRAME_FEND  = 0xC0;   // байт Frame END
    private static final int WAKE_FRAME_FESC  = 0xDB;   // байт Frame ESCape
    private static final int WAKE_FRAME_TFEND = 0xDC;   // байт Transposed Frame END
    private static final int WAKE_FRAME_TFESC = 0xDD;   // байт Transposed Frame ESCape

    // Коды ошибок
    private static final int WAKE_ERR_NO      = 0x00;   // нет ошибки
    private static final int WAKE_ERR_TX      = 0x01;   // ошибка обмена
    private static final int WAKE_ERR_BU      = 0x02;   // устройство занято
    private static final int WAKE_ERR_RE      = 0x03;   // устройство не готово
    private static final int WAKE_ERR_PA      = 0x04;   // неправильные команды
    private static final int WAKE_ERR_NR      = 0x05;   // устройство не отвечает

    // Состояния приема/передачи пакета протокола.
    private static final int WAKE_STATE_FEND  = 0;      // ожидание приема стартового байта Frame END
    private static final int WAKE_STATE_ADDR  = 1;      // ожидание приема адреса
    private static final int WAKE_STATE_CMD   = 2;      // ожидание приема команды
    private static final int WAKE_STATE_NBT   = 3;      // ожидание приема количества данных
    private static final int WAKE_STATE_DATA  = 4;      // состояние приема данных
    private static final int WAKE_STATE_CRC   = 5;      // ожидание приема контрольной суммы CRC8

    // Зарезервированные адреса
    public static final int WAKE_ADDR_NO      = 0x00;   //без адреса
    public static final int WAKE_ADDR_BRDCST  = 0x80;   // широковещательный адрес

    // Коды универсальных команд
    public static final int WAKE_CMD_NOP      = 0;      // нет операции
    public static final int WAKE_CMD_ERR      = 1;      // ошибка приема пакета
    public static final int WAKE_CMD_ECHO     = 2;      // передать эхо
    public static final int WAKE_CMD_INFO     = 3;      // передать информацию об устройстве
    public static final int WAKE_CMD_STATUS   = 4;      // прочитать данные с устройства
    public static final int WAKE_CMD_SETCLR   = 5;      // установить цвет
    public static final int WAKE_CMD_SETTMR   = 6;      // установить время автовыключения
    public static final int WAKE_CMD_ENABLE   = 7;      // включить/выключить устройство
    public static final int WAKE_CMD_COMOFF   = 8;      // выключить bluetooth

    private static final String TAG = "BTWAKE";

    // BroadcastReceiver для ACTION_FOUND
    public final BroadcastReceiver btSearchReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            // Когда найдено новое устройство
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Получаем объект BluetoothDevice из интента
                BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                String btDevice_name = btDevice.getName();;

                if (btDevice_name == null) {
                    btDevice_name = "UNKNOWN";
                }

                Log.d(TAG, "WAKE. Found new device");

                if (btListener != null) {
                    btListener.onFindDevice(new btDevice(btDevice.getAddress(), btDevice_name, false, false, false));
                }
            }
        }
    };

    private Handler btConnection_h = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case BT_EVENT_DICOVERING_START:
                    Log.d(TAG, "WAKE. Start discovery");
                    if (btListener != null) {btListener.onStartDiscovery();}
                    break;
                case BT_EVENT_DISCOVERING_CANCEL:
                    Log.d(TAG, "WAKE. Cancel discovery");
                    if (btListener != null) {btListener.onCancelDiscovery();}
                    break;
                case BT_EVENT_CONNECT_OK:
                    // Установка соединения
                    Log.d(TAG, "WAKE. Connecting to device");
                    btStatus = BT_STATUS_CONNECT;
                    if (btListener != null) {btListener.onConnect();}
                    break;
                case BT_EVENT_CONNECT_ERR:
                    // Ошибка подключения к устройству
                    Log.d(TAG, "WAKE. Connecting error");
                    if(btAdapter.isEnabled()){btStatus = BT_STATUS_ENABLE;} else {btStatus = BT_STATUS_DISABLE;}
                    if (btListener != null) {btListener.onConnectError();}
                    break;
                case BT_EVENT_STREAM_ERR:
                    // Ошибка создания потоков данных при подключении к устройству
                    Log.d(TAG, "WAKE. Connecting error");
                    if(btAdapter.isEnabled()){btStatus = BT_STATUS_ENABLE;} else {btStatus = BT_STATUS_DISABLE;}
                    if (btListener != null) {btListener.onConnectError();}
                    break;
                case BT_EVENT_IO_STREAM_ERR:
                    // Ошибка передачи данных
                    if (btStatus == BT_STATUS_CONNECT){
                        // Обрыв связи
                        if(btAdapter.isEnabled()){btStatus = BT_STATUS_ENABLE;} else {btStatus = BT_STATUS_DISABLE;}
                        if (btStatus == BT_STATUS_ENABLE){
                            // Отключение устройства
                            Log.d(TAG, "WAKE. Device is disconnecting");
                            if (btListener != null) {btListener.onConnectIoError();}
                        } else {
                            // Выключение bluetooth модуля
                            Log.d(TAG, "WAKE. Bluetooth module is disable");
                            if (btListener != null) {btListener.onConnectEnableError();}
                            if(btAdapter.isEnabled()){btStatus = BT_STATUS_ENABLE;} else {btStatus = BT_STATUS_DISABLE;}
                        }

                    } else if (btStatus == BT_STATUS_ENABLE){
                        // Программное завершение соединения
                        Log.d(TAG, "WAKE. Cancel connecting");
                        if(btAdapter.isEnabled()){btStatus = BT_STATUS_ENABLE;} else {btStatus = BT_STATUS_DISABLE;}
                        if (btListener != null) {btListener.onDisconnect();}
                    }
                    break;
                case WAKE_EVENT_RX_PACKAGE:
                    if (btListener != null) {
                        if (msg.obj != null) {
                            btListener.onRxPackage(msg.arg1, msg.arg2, (int[])msg.obj);
                        } else {
                            btListener.onRxPackage(msg.arg1, msg.arg2, null);
                        }
                    }
                    break;
            }
        }
    };

    public interface OnEventListener {

        void onStartDiscovery();
        void onCancelDiscovery();
        void onFindDevice(btDevice device);
        void onConnect();
        void onDisconnect();
        void onConnectError();
        void onConnectEnableError();
        void onConnectIoError();
        void onRxPackage(int cmd, int nbt, int[] data);
    }


    public static BluetoothWake getInstance() {

        return mInstance;
    }

    private BluetoothWake() {

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter != null){
            if (btAdapter.isEnabled()){
                btStatus = BT_STATUS_ENABLE;
            } else {
                btStatus = BT_STATUS_DISABLE;
            }
        } else {
            btStatus = BT_STATUS_NOT_AVAILABLE;
        }
        btWakeAddr = WAKE_ADDR_NO;
    }

    public void setEventListener(OnEventListener listener){
        if (listener != null){
            btListener = listener;
        }
    }

    public void startStatusTask(){
        if (btStatus > BT_STATUS_NOT_AVAILABLE) {
            btCheckStatusTask = new btCheckStatusThread();
            btCheckStatusTask.start();
        }
    }

    public void cancelStatusTask(){
        if (btStatus > BT_STATUS_NOT_AVAILABLE) {
            if (btCheckStatusTask.isAlive()){
                btCheckStatusTask.interrupt();
            }
        }
    }

    public boolean isAvailable() {
        if (btStatus > BT_STATUS_NOT_AVAILABLE) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isEnable() {
        if (btAdapter.isEnabled()) {
            return true;
        } else {
            return false;
        }
    }

    public void getPairedDevices(){

        if (btAdapter.isEnabled()){

            boolean btAvaibleUUID;
            String btPeriedDevice_name;
            Set<BluetoothDevice> btPeriedDevicesSet = btAdapter.getBondedDevices();

            // Если список спаренных устройств не пуст
            if(btPeriedDevicesSet.size() > 0) {
                // проходимся в цикле по этому списку
                for(BluetoothDevice btPeriedDevice: btPeriedDevicesSet){

                    btPeriedDevice_name = btPeriedDevice.getName();
                    if (btPeriedDevice_name == null) {btPeriedDevice_name = "UNKNOWN";}

                    btAvaibleUUID = false;
                    for (ParcelUuid device_uuid: btPeriedDevice.getUuids()) {
                        if (device_uuid.toString().equalsIgnoreCase(SPP_UUID)){
                            btAvaibleUUID = true;
                            break;
                        }
                    }

                    Log.d(TAG, "Found new paried device");

                    if (btListener != null) {
                        btListener.onFindDevice(new btDevice(btPeriedDevice.getAddress(), btPeriedDevice_name, true, btAvaibleUUID, false));
                    }
                }
            }
        }
    }

    public void registerReceiver(Context context){
        if (btStatus > BT_STATUS_NOT_AVAILABLE) {
            // Останавливаем поиск устройств
            btAdapter.cancelDiscovery();
            // Регистрируем BroadcastReceiver
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            context.registerReceiver(btSearchReceiver, filter);

            Log.d(TAG, "Register receiver");
        }
    }

    public void unregisterReceiver(Context context){
        if (btStatus > BT_STATUS_NOT_AVAILABLE) {
            // Останавливаем поиск устройств
            btAdapter.cancelDiscovery();
            // Снимаем регистрацию BroadcastReceiver
            context.unregisterReceiver(btSearchReceiver);

            Log.d(TAG, "Unregister receiver");
        }
    }

    public void startDiscovering(){
        if (btAdapter.isEnabled()) {
            if (!btAdapter.isDiscovering()) {
                btAdapter.startDiscovery();
            }
        }
    }

    public void cancelDiscovering(){
        if (btAdapter.isEnabled()) {
            if (btAdapter.isDiscovering()) {
                btAdapter.cancelDiscovery();
            }
        }
    }

    public boolean isDiscovering(){

        if (btAdapter.isEnabled()) {
            return btAdapter.isDiscovering();
        } else {
            return false;
        }
    }

    public boolean connectDevice(btDevice device){

        if (btAdapter.isEnabled()){
            if (device != null) {
                btConnectionTask = new btConnectionThread(device.addr);
                btConnectionTask.start();
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public void disconnectDevice(){
        if (btStatus == BT_STATUS_CONNECT){
            if (btConnectionTask.isAlive()){
                btConnectionTask.cancel();
            }
        }
    }

    public void sendPackage(int addr, int cmd, int nbt, int[] data){
        if (btStatus == BT_STATUS_CONNECT){
            if (btConnectionTask.isAlive()){
                btConnectionTask.transmit(addr, cmd, nbt, data);
            }
        }
    }

    public void setAddress(int addr){

        if ((addr > WAKE_ADDR_BRDCST) && (addr < 256)){
            btWakeAddr = addr;
        } else {
            btWakeAddr = WAKE_ADDR_NO;
        }
    }

    private class btCheckStatusThread extends Thread {

        @Override
        public void run() {

            boolean current_state;
            boolean discover_state = btAdapter.isDiscovering();

            do {

                current_state = btAdapter.isDiscovering();
                if (current_state != discover_state) {
                    // Изменилось состояние поиска
                    discover_state = current_state;
                    if (discover_state){
                        // Сообщение о начале поиска устройств
                        btConnection_h.sendEmptyMessage(BT_EVENT_DICOVERING_START);
                    } else {
                        // Сообщение об окончании поиска устройств
                        btConnection_h.sendEmptyMessage(BT_EVENT_DISCOVERING_CANCEL);
                    }
                }

                //Проверка прерывания
                if(Thread.interrupted())
                {
                    break;
                } else {
                    // Приостановка потока на 500 мсек
                    try{
                        this.sleep(500);
                    } catch(InterruptedException e){
                        // Завершение потока после прерывания
                        break;
                    }
                }

            } while (true);

        }
    }

    private class btConnectionThread extends Thread {

        private BluetoothSocket socket;
        private InputStream input_stream;
        private OutputStream output_stream;

        private int rx_state;
        private boolean rx_fesc_event;
        private int rx_data_counter;
        private int rx_addr;
        private int rx_cmd;
        private int rx_nbt;
        private int[] rx_data = new int[256];


        public btConnectionThread(String addr) {
            BluetoothDevice device = null;
            BluetoothSocket tmp_socket = null;

            try {
                UUID spp_uuid = UUID.fromString(SPP_UUID);
                device = btAdapter.getRemoteDevice(addr);
                tmp_socket = device.createRfcommSocketToServiceRecord(spp_uuid);
            } catch (IOException e) {}
            socket = tmp_socket;
        }

        public void run() {
            InputStream tmp_input = null;
            OutputStream tmp_output = null;
            byte[] rx_buf = new byte[256];
            int rx_bytes = 0;
            int rx_count = 0;

            // Отменяем сканирование, поскольку оно тормозит соединение
            btAdapter.cancelDiscovery();

            // Приостановка потока на 250 мсек
            try{
                this.sleep(250);
            } catch(InterruptedException e){
                // Завершение потока после прерывания
                try {
                    socket.close();
                    btConnection_h.sendEmptyMessage(BT_EVENT_CONNECT_ERR);
                    return;
                } catch (IOException e2) {
                    btConnection_h.sendEmptyMessage(BT_EVENT_CONNECT_ERR);
                    return;
                }
            }

            try {
                // Попытка соединиться
                socket.connect();
            } catch (IOException e) {
                // Невозможно соединиться. Закрываем сокет и выходим.
                try {
                    socket.close();
                    btConnection_h.sendEmptyMessage(BT_EVENT_CONNECT_ERR);
                    return;
                } catch (IOException e2) {
                    btConnection_h.sendEmptyMessage(BT_EVENT_CONNECT_ERR);
                    return;
                }
            }

            try {
                // Получить входящий и исходящий потоки данных
                tmp_input = socket.getInputStream();
                tmp_output = socket.getOutputStream();
            } catch (IOException e) {
                // Невозможно создать потоки. Закрываем сокет и выходим.
                try {
                    socket.close();
                    btConnection_h.sendEmptyMessage(BT_EVENT_STREAM_ERR);
                    return;
                } catch (IOException e2) {
                    btConnection_h.sendEmptyMessage(BT_EVENT_STREAM_ERR);
                    return;
                }
            }
            input_stream = tmp_input;
            output_stream = tmp_output;
            this.reset();

            btConnection_h.sendEmptyMessage(BT_EVENT_CONNECT_OK);

            while (true) {
                try {
                    // Получаем кол-во байт и само собщение в байтовый массив
                    rx_bytes = input_stream.read(rx_buf);
                    // Побайтовая обработка массива
                    rx_count = 0;
                    while (rx_count < rx_bytes){
                        this.receive(rx_buf[rx_count]);
                        rx_count++;
                    }
                } catch (IOException e) {
                    // Если ошибка приема сообщения. Закрываем сокет и выходим.
                    try {
                        input_stream.close();
                        output_stream.close();
                        socket.close();
                        btConnection_h.sendEmptyMessage(BT_EVENT_IO_STREAM_ERR);
                        break;
                    } catch (IOException e2) {
                        btConnection_h.sendEmptyMessage(BT_EVENT_IO_STREAM_ERR);
                        break;
                    }
                }
            }
        }

        private void receive(byte data){

            if ((0x000000FFL & ((int)data)) == WAKE_FRAME_FEND) {
                // принят управляющий код FEND
                rx_state = WAKE_STATE_ADDR;
                return;
            } else if (rx_state != WAKE_STATE_FEND) {
                // принимаем пакет данных
                if ((0x000000FFL & ((int)data)) == WAKE_FRAME_FESC) {
                    if (!rx_fesc_event) {
                        // принят управляющий код FESC
                        rx_fesc_event = true;
                    } else {
                        reset();
                    }
                    return;
                } else {
                    // приняты данные пакета отличные от управляющих кодов FEND и FESC
                    if (rx_fesc_event) {
                        // байт-стаффинг
                        rx_fesc_event = false;
                        if ((0x000000FF & ((int)data)) == WAKE_FRAME_TFEND) {
                            data = (byte)(WAKE_FRAME_FEND & 0x000000FFL);
                        } else if ((0x000000FF & ((int)data)) == WAKE_FRAME_TFESC) {
                            data = (byte)(WAKE_FRAME_FESC & 0x000000FFL);
                        } else {
                            this.reset();
                            return;
                        }
                    }
                }
            } else {
                // байт FEND не получен, данные игнорируются
                return;
            }

            switch (rx_state) {
                case WAKE_STATE_FEND:
                    break;
                case WAKE_STATE_ADDR:
                    // если бит 7 данных не равен нулю, то это адрес
                    if (((0x000000FF & ((int) data)) & 0x00000080) != 0) {
                        rx_addr = (0x000000FF & ((int) data));
                        // если широковещательный или верный адрес
                        if ((rx_addr == WAKE_ADDR_BRDCST) || (rx_addr == btWakeAddr)) {
                            rx_state = WAKE_STATE_CMD;
                        } else {
                            // адрес не совпал, ожидание нового пакета
                            this.reset();
                        }
                        break;
                    } else {
                        // переходим к приему команды
                        rx_state = WAKE_STATE_CMD;
                        rx_addr = WAKE_ADDR_NO;
                    }
                case WAKE_STATE_CMD:
                    // если бит 7 данных не равен нулю, то ошибка
                    if (((0x000000FF & ((int) data)) & 0x00000080) != 0) {
                        this.reset();
                    } else {
                        // сохранение команды
                        rx_cmd   = (0x000000FF & ((int) data));
                        rx_state = WAKE_STATE_NBT;
                    }
                    break;
                case WAKE_STATE_NBT:
                    rx_nbt = (0x000000FF & ((int) data));
                    if (rx_nbt > 0) {
                        rx_state = WAKE_STATE_DATA;
                    } else {
                        // Формируем сообщение с принятым пакетом
                        btConnection_h.obtainMessage(WAKE_EVENT_RX_PACKAGE, rx_cmd, rx_nbt, null).sendToTarget();
                        this.reset();
                    }
                    break;
                case WAKE_STATE_DATA:

                    rx_data[rx_data_counter] = (0x000000FF & ((int) data));
                    rx_data_counter++;

                    if (rx_data_counter >= rx_nbt) {
                        // Формируем сообщение с принятым пакетом
                        btConnection_h.obtainMessage(WAKE_EVENT_RX_PACKAGE, rx_cmd, rx_nbt, rx_data).sendToTarget();
                        this.reset();
                    }
                    break;
            }
        }

        private void reset(){
            rx_state        = WAKE_STATE_FEND;
            rx_fesc_event   = false;
            rx_data_counter = 0;
            rx_addr         = WAKE_ADDR_NO;
        }

        public void transmit(int addr, int cmd, int nbt, int[] data) {

            int tx_counter   = 0;
            int data_counter = 0;
            int frame_part   = WAKE_STATE_FEND;
            byte[] tx_buf = new byte[256];

            do {
                switch (frame_part) {
                    case WAKE_STATE_FEND:
                        tx_buf[0] = (byte)(WAKE_FRAME_FEND & 0x000000FFL);
                        frame_part = WAKE_STATE_ADDR;
                        break;
                    case WAKE_STATE_ADDR:
                        if (addr != WAKE_ADDR_NO) {
                            tx_buf[++tx_counter] = (byte)((addr | 0x00000080L) & 0x000000FFL);
                        }
                        frame_part = WAKE_STATE_CMD;
                        break;
                    case WAKE_STATE_CMD:
                        tx_buf[++tx_counter] = (byte)(cmd & 0x0000007FL);
                        frame_part = WAKE_STATE_NBT;
                        break;
                    case WAKE_STATE_NBT:
                        tx_buf[++tx_counter] = (byte)(nbt & 0x000000FFL);
                        frame_part = WAKE_STATE_DATA;
                        break;
                    case WAKE_STATE_DATA:
                        if (data != null) {
                            if (data_counter < nbt) {
                                tx_buf[++tx_counter] = (byte)(data[data_counter++] & 0x000000FFL);
                                break;
                            } else {
                                frame_part = WAKE_STATE_CRC;
                                break;
                            }
                        } else {
                            frame_part = WAKE_STATE_CRC;
                            break;
                        }
                    case WAKE_STATE_CRC:
                        break;
                }
            } while (frame_part < WAKE_STATE_CRC);

            // байт-стаффинг
            if (tx_counter > 0)
            {
                if ((0x000000FF & ((int)tx_buf[tx_counter])) == WAKE_FRAME_FEND)
                {
                    tx_buf[tx_counter] = (byte)(WAKE_FRAME_FESC & 0x000000FFL);
                    tx_buf[++tx_counter] = (byte)(WAKE_FRAME_TFEND & 0x000000FFL);
                }
                else if ((0x000000FF & ((int)tx_buf[tx_counter])) == WAKE_FRAME_FESC)
                {
                    tx_buf[++tx_counter] = (byte)(WAKE_FRAME_TFESC & 0x000000FFL);
                }
            }

            try {
                output_stream.write(tx_buf, 0, tx_counter + 1);

            } catch (IOException e) {
                // Если ошибка передачи сообщения. Закрываем сокет и выходим.
                try {
                    input_stream.close();
                    output_stream.close();
                    socket.close();
                    btConnection_h.sendEmptyMessage(BT_EVENT_IO_STREAM_ERR);
                } catch (IOException e2) {
                    btConnection_h.sendEmptyMessage(BT_EVENT_IO_STREAM_ERR);
                }
            }
        }

        public void cancel() {
            try {
                btStatus = BT_STATUS_ENABLE;
                socket.close();
            } catch (IOException e) { }
        }
    }
}
