package com.embedhome.btlamp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.embedhome.btlamp.wake.BluetoothWake;
import com.embedhome.btlamp.wake.btDevice;


public class MainActivity extends AppCompatActivity implements View.OnClickListener, BluetoothWake.OnEventListener {

    private Toolbar toolbar;
    private FloatingActionButton fab_search;
    private ImageView fab_anim_image;
    private RecyclerView btdevice_list;

    private ImageView dialog_image;
    private ProgressBar dialog_progress;
    private TextView dialog_title;
    private TextView dialog_message;
    private Button dialog_positive;
    private Button dialog_negative;
    private MenuItem item_notification;

    private BluetoothWake btWake;
    private btDevice last_device;
    private volatile int connect_attemp;
    private CountDownTimer connect_timer;

    private AppNotification app_notification;

    private static final int BT_REQUEST_ENABLE = 1;
    private static final int BT_REQUEST_CONNECT = 2;

    private static final int BT_RESULT_DISCONNECT = 0;
    private static final int BT_RESULT_ERROR = 1;
    private static final int BT_RESULT_DISABLE = 2;
    private static final int BT_CONNECT_ATTEMPS = 33;

    private View.OnClickListener onClickFinishListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            finish();
        }
    };

    private View.OnClickListener onClickEnableListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // Bluetooth выключен. Предложим пользователю включить его.
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, BT_REQUEST_ENABLE);
        }
    };

    private View.OnClickListener onClickTryListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            // Повторяем попытку подключения
            connect_attemp = BT_CONNECT_ATTEMPS;
            if (btWake.connectDevice(last_device)){
                // Открываем диалог соединения
                repeatConnectDialog();
            }
        }
    };
    private View.OnClickListener onClickCancelListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // скрываем диалог включения bluetooth
            returnDeviceList();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        toolbar = (Toolbar) findViewById(R.id.connect_toolbar);
        setSupportActionBar(toolbar);

        app_notification = new AppNotification(this);

        fab_search = (FloatingActionButton) findViewById(R.id.fab_search);
        fab_search.setOnClickListener(this);
        fab_anim_image = (ImageView) findViewById(R.id.fab_anim);

        btdevice_list = (RecyclerView) findViewById(R.id.btdevice_list);
        btdevice_list.setLayoutManager(new GridLayoutManager(this, 2));

        RecyclerView.ItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setAddDuration(350);
        itemAnimator.setRemoveDuration(350);
        btdevice_list.setItemAnimator(itemAnimator);

        btdevice_list.setAdapter(new btDeviceAdapter(new btDeviceAdapter.OnSelectDeviceListener() {
            @Override
            public void onSelectDevice(btDevice item) {

                if (btWake.isEnable()) {
                    if ((item.avaible) || (!item.paried)) {

                        last_device = new btDevice(item.addr, item.name, item.paried, item.avaible, item.favorite);
                        connect_attemp = BT_CONNECT_ATTEMPS;
                        if (btWake.connectDevice(last_device)){
                            // Открываем диалог соединения
                            showConnectDialog();
                        }
                    }
                } else {
                    showBtDisableDialog();
                }
            }
        }));

        dialog_image = (ImageView) findViewById(R.id.connect_dialog_image);
        dialog_progress = (ProgressBar) findViewById(R.id.connect_dialog_progress);
        dialog_progress.setMax(BT_CONNECT_ATTEMPS);
        dialog_title = (TextView) findViewById(R.id.connect_dialog_title);
        dialog_message = (TextView) findViewById(R.id.connect_dialog_message);
        dialog_positive = (Button) findViewById(R.id.connect_dialog_pbutton);
        dialog_negative = (Button) findViewById(R.id.connect_dialog_nbutton);

        // получаем класс адаптера bluetooth
        btWake = BluetoothWake.getInstance();
        btWake.setEventListener(this);
        btWake.startStatusTask();

        if (btWake.isAvailable()) {
            if (btWake.isEnable()) {
                // Получаем список спаренных устройств
                btWake.getPairedDevices();
                btdevice_list.setVisibility(View.VISIBLE);
                fab_search.setVisibility(View.VISIBLE);
            } else {
                // отображаем диалог включения bluetooth
                dialog_image.setScaleX(1f);
                dialog_image.setScaleY(1f);
                dialog_image.setAlpha(1f);
                dialog_image.setVisibility(View.VISIBLE);
                dialog_title.setAlpha(1f);
                dialog_title.setText(R.string.textErrorOops);
                dialog_title.setVisibility(View.VISIBLE);
                dialog_message.setAlpha(1f);
                dialog_message.setText(R.string.messageBtDisabled);
                dialog_message.setVisibility(View.VISIBLE);
                dialog_positive.setAlpha(1f);
                dialog_positive.setText(R.string.textActionBtEnable);
                dialog_positive.setOnClickListener(onClickEnableListener);
                dialog_positive.setVisibility(View.VISIBLE);
                dialog_negative.setAlpha(1f);
                dialog_negative.setText(R.string.textActionNoAndExit);
                dialog_negative.setOnClickListener(onClickFinishListener);
                dialog_negative.setVisibility(View.VISIBLE);
            }
        } else {
            // отображаем диалог отсутствия модуля bluetooth
            dialog_image.setScaleX(1f);
            dialog_image.setScaleY(1f);
            dialog_image.setAlpha(1f);
            dialog_image.setVisibility(View.VISIBLE);
            dialog_title.setAlpha(1f);
            dialog_title.setText(R.string.textErrorSorry);
            dialog_title.setVisibility(View.VISIBLE);
            dialog_message.setAlpha(1f);
            dialog_message.setText(R.string.messageBtNotAvaible);
            dialog_message.setVisibility(View.VISIBLE);
            dialog_positive.setAlpha(1f);
            dialog_positive.setText(R.string.tetxActionExit);
            dialog_positive.setOnClickListener(onClickFinishListener);
            dialog_positive.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_connect, menu);

        item_notification = menu.findItem(R.id.menu_connect_notification);
        switch (app_notification.getNotification()) {
            case AppNotification.NOTIFICATION_DISABLE:
                item_notification.setIcon(R.drawable.imageMenuNotificationSoundOff);
                break;
            case AppNotification.NOTIFICATION_RINGTON:
                item_notification.setIcon(R.drawable.imageMenuNotificationSoundOn);
                break;
            case AppNotification.NOTIFICATION_VIBRO:
                item_notification.setIcon(R.drawable.imageMenuNotificationVibration);
                break;
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.menu_connect_notification:

                switch (app_notification.getNotification()) {
                    case AppNotification.NOTIFICATION_DISABLE:
                        app_notification.setNotification(AppNotification.NOTIFICATION_RINGTON);
                        item_notification.setIcon(R.drawable.imageMenuNotificationSoundOn);
                        app_notification.show();
                        break;
                    case AppNotification.NOTIFICATION_RINGTON:
                        app_notification.setNotification(AppNotification.NOTIFICATION_VIBRO);
                        item_notification.setIcon(R.drawable.imageMenuNotificationVibration);
                        app_notification.show();
                        break;
                    case AppNotification.NOTIFICATION_VIBRO:
                        app_notification.setNotification(AppNotification.NOTIFICATION_DISABLE);
                        item_notification.setIcon(R.drawable.imageMenuNotificationSoundOff);
                        break;
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onStart() {

        super.onStart();
        // Регистрируем BroadcastReceiver
        btWake.registerReceiver(this);
    }

    @Override
    protected void onStop() {

        super.onStop();
        // Снимаем регистрацию BroadcastReceiver
        btWake.unregisterReceiver(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        btWake.disconnectDevice();
        btWake.cancelStatusTask();
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        switch (app_notification.updateNotification(this)) {
            case AppNotification.NOTIFICATION_DISABLE:
                item_notification.setIcon(R.drawable.imageMenuNotificationSoundOff);
                break;
            case AppNotification.NOTIFICATION_RINGTON:
                item_notification.setIcon(R.drawable.imageMenuNotificationSoundOn);
                break;
            case AppNotification.NOTIFICATION_VIBRO:
                item_notification.setIcon(R.drawable.imageMenuNotificationVibration);
                break;
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == BT_REQUEST_ENABLE) {
            if (resultCode == RESULT_OK) {
                if (btWake.isEnable()){
                    returnDeviceList();
                    // получаем список устройств
                    btWake.getPairedDevices();
                }
            }
        }

        if (requestCode == BT_REQUEST_CONNECT) {

            btWake.setEventListener(this);
            switch (resultCode) {
                case BT_RESULT_DISCONNECT:
                    // Отображаем список устройств
                    getShowDeviceListAnimation().start();
                    break;
                case BT_RESULT_ERROR:
                    // отображаем диалог ошибки подключения
                    getShowDialogAnimation(R.string.textErrorOops,
                            R.string.messageConnectError,
                            R.string.textActionTryAgain,
                            R.string.textActionBackToList,
                            onClickTryListener,
                            onClickCancelListener).start();
                    break;
                case BT_RESULT_DISABLE:
                    // Очищаем список устройств в RecycledView
                    ((btDeviceAdapter) btdevice_list.getAdapter()).clearItems();
                    // отображаем диалог включения bluetooth
                    getShowDialogAnimation(R.string.textErrorOops,
                            R.string.messageBtDisabled,
                            R.string.textActionBtEnable,
                            R.string.textActionNoAndExit,
                            onClickEnableListener,
                            onClickFinishListener).start();
                    break;
            }
        }
    }

    @Override
    public void onClick(View v) {

        if (btWake.isEnable()){
            if (btWake.isDiscovering()) {
                btWake.cancelDiscovering();
            } else {
                ((btDeviceAdapter) btdevice_list.getAdapter()).clearFoundItems();
                btWake.startDiscovering();
            }
        } else {
            showBtDisableDialog();
        }
    }

    @Override
    public void onBackPressed() {
        if (btdevice_list.getVisibility() == View.VISIBLE){
            showExitDialog();
        }
    }

    @Override
    public void onStartDiscovery() {
        if (fab_search.getVisibility() == View.VISIBLE){
            fab_search.animate().rotation(45).setDuration(300);
            fab_anim_image.setVisibility(View.VISIBLE);
            Animation search_anim = AnimationUtils.loadAnimation(MainActivity.this, R.anim.search_anim);
            fab_anim_image.startAnimation(search_anim);
        }
    }

    @Override
    public void onCancelDiscovery() {
        if (fab_search.getVisibility() == View.VISIBLE){
            fab_search.animate().rotation(0).setDuration(300);
            fab_anim_image.clearAnimation();
            fab_anim_image.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onFindDevice(btDevice device) {
        // Добавляем устройства в RecycledView
        ((btDeviceAdapter) btdevice_list.getAdapter()).addItem(device);

        if(!device.paried){
            app_notification.show();
        }
    }

    @Override
    public void onConnect() {

        // Если устройство было не сопряжено меняем статус
        if (last_device.paried == false){
            ((btDeviceAdapter) btdevice_list.getAdapter()).setPariedDevice(last_device);
        }
        dialog_progress.setProgress(BT_CONNECT_ATTEMPS);
        dialog_title.setText(getString(R.string.textFormatPercent, (float)dialog_progress.getProgress()/BT_CONNECT_ATTEMPS *100));

        btWake.sendPackage(btWake.WAKE_ADDR_NO, btWake.WAKE_CMD_INFO, 0, null);
        connect_timer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {

            }
            @Override
            public void onFinish() {
                btWake.disconnectDevice();
                // Не получен ответ от устройства за отведенное время
                showConnectErrorDialog(R.string.messageSmartDeviceConnectError);
            }
        }.start();
    }

    @Override
    public void onDisconnect() {
        // Если произошел программный разрыв связи
        showConnectErrorDialog(R.string.messageConnectError);
    }

    @Override
    public void onConnectError() {

        // Если при соединении произошла ошибка
        connect_attemp--;
        if (connect_attemp > 0){
            // Повторяем попытку соединения
            dialog_progress.setProgress(BT_CONNECT_ATTEMPS - connect_attemp);
            dialog_title.setText(getString(R.string.textFormatPercent, (float)dialog_progress.getProgress()/BT_CONNECT_ATTEMPS *100));

            if (!btWake.connectDevice(last_device)){
                // Если не удалась попытка то вызываем диалог ошибки
                showConnectErrorDialog(R.string.messageConnectError);
            }
        } else {
            // Если исчерпали количество повторов то вызываем диалог ошибки
            showConnectErrorDialog(R.string.messageConnectError);
        }
    }

    @Override
    public void onConnectEnableError() {
        // Если при соединении был выключен модуль bluetooth
        showBtEnableErrorDialog();
    }

    @Override
    public void onConnectIoError() {
        // Если при обмене данными произошла ошибка
        showConnectErrorDialog(R.string.messageConnectError);
    }

    @Override
    public void onRxPackage(int cmd, int nbt, int[] data) {

        String device_ver = new String();

        if (cmd == btWake.WAKE_CMD_INFO) {

            if (data != null) {
                for (int i = 0; i < nbt; i++){
                    device_ver = device_ver + (char)data[i];
                }
            }

            connect_timer.cancel();
            // скрываем диалог подключения
            getHideConnectAnimation().start();

            // Подключаемся к устройству
            Intent intent = new Intent(getApplicationContext(), LampActivity.class);
            intent.putExtra("name", last_device.name);
            intent.putExtra("addr", last_device.addr);
            intent.putExtra("version", device_ver);
            startActivityForResult(intent, BT_REQUEST_CONNECT);
        } else {
            connect_timer.cancel();
            btWake.disconnectDevice();
            // Получен не правильный ответ
            showConnectErrorDialog(R.string.messageSmartDeviceConnectError);
        }
    }

    private void showExitDialog(){

        // воспроизводим анимацию
        AnimatorSet anim = new AnimatorSet();
        anim.playSequentially(
                getHideDiviceListAnimation(),
                getShowDialogAnimation(R.string.textErrorOops,
                        R.string.messageAppQuit,
                        R.string.textActionNoAndReturn,
                        R.string.textActionYesAndExit,
                        onClickCancelListener,
                        onClickFinishListener)
        );
        anim.start();
    }

    private void showBtDisableDialog(){

        // воспроизводим анимацию
        AnimatorSet anim = new AnimatorSet();
        anim.playSequentially(
                getHideDiviceListAnimation(),
                getShowDialogAnimation(R.string.textErrorOops,
                        R.string.messageBtDisabled,
                        R.string.textActionBtEnable,
                        R.string.textActionNoAndExit,
                        onClickEnableListener,
                        onClickFinishListener)
        );
        anim.start();
        // Очищаем список устройств в RecycledView
        ((btDeviceAdapter) btdevice_list.getAdapter()).clearItems();
        app_notification.show();
    }

    private void returnDeviceList(){

        // воспроизводим анимацию
        AnimatorSet anim = new AnimatorSet();
        anim.playSequentially(
                getHideDialogAnimation(),
                getShowDeviceListAnimation()
        );
        anim.start();
    }

    private void showConnectDialog() {

        // воспроизводим анимацию
        AnimatorSet anim = new AnimatorSet();
        anim.playSequentially(
                getHideDiviceListAnimation(),
                getShowConnectAnimation()
        );
        anim.start();
    }

    private void showBtEnableErrorDialog(){

        // Очищаем список устройств в RecycledView
        ((btDeviceAdapter) btdevice_list.getAdapter()).clearItems();
        // воспроизводим анимацию
        AnimatorSet anim = new AnimatorSet();
        anim.playSequentially(
                getHideConnectAnimation(),
                getShowDialogAnimation(R.string.textErrorOops,
                        R.string.messageBtDisabled,
                        R.string.textActionBtEnable,
                        R.string.textActionNoAndExit,
                        onClickEnableListener,
                        onClickFinishListener)
        );
        anim.start();
        app_notification.show();
    }

    private void showConnectErrorDialog(int idMessageError){

        // воспроизводим анимацию
        AnimatorSet anim = new AnimatorSet();
        anim.playSequentially(
                getHideConnectAnimation(),
                getShowDialogAnimation(R.string.textErrorOops,
                                       idMessageError,
                                       R.string.textActionTryAgain,
                                       R.string.textActionBackToList,
                                       onClickTryListener,
                                       onClickCancelListener)
        );
        anim.start();
        app_notification.show();
    }

    private void repeatConnectDialog(){

        // воспроизводим анимацию
        AnimatorSet anim = new AnimatorSet();
        anim.playSequentially(
                getHideDialogAnimation(),
                getShowConnectAnimation()
        );
        anim.start();
    }

    private AnimatorSet getShowDeviceListAnimation(){

        AnimatorListenerAdapter showAnimatorListener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                btdevice_list.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                // востанавливаем состояние fab
                fab_search.show();
                if (btWake.isDiscovering()){
                    fab_search.animate().rotation(45).setDuration(300);
                    fab_anim_image.setVisibility(View.VISIBLE);
                    Animation search_anim = AnimationUtils.loadAnimation(MainActivity.this, R.anim.search_anim);
                    fab_anim_image.startAnimation(search_anim);
                } else {
                    fab_search.animate().rotation(0).setDuration(300);
                }
            }
        };

        AnimatorSet expand_anim = new AnimatorSet();
        expand_anim.playTogether(
                ObjectAnimator.ofFloat(btdevice_list, btdevice_list.SCALE_X, 1f),
                ObjectAnimator.ofFloat(btdevice_list, btdevice_list.SCALE_Y, 1f),
                ObjectAnimator.ofFloat(btdevice_list, btdevice_list.ALPHA, 1f)
        );
        expand_anim.setDuration(250);
        expand_anim.setInterpolator(new OvershootInterpolator());

        AnimatorSet slide_anim = new AnimatorSet();
        slide_anim.playTogether(
                ObjectAnimator.ofFloat(btdevice_list, btdevice_list.X, 0)
        );
        slide_anim.setDuration(250);
        slide_anim.setInterpolator(new DecelerateInterpolator());

        AnimatorSet show_anim = new AnimatorSet();
        show_anim.playSequentially(
                slide_anim,
                expand_anim
        );
        show_anim.addListener(showAnimatorListener);

        return show_anim;
    }

    private AnimatorSet getHideDiviceListAnimation(){

        AnimatorListenerAdapter hideAnimatorListener = new AnimatorListenerAdapter() {

            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                // останавливаем и скрываем анимацию
                fab_anim_image.clearAnimation();
                fab_anim_image.setVisibility(View.INVISIBLE);
                // скрываем fab
                fab_search.hide();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                // скрываем список устройств
                btdevice_list.setVisibility(View.INVISIBLE);
            }
        };

        AnimatorSet collaps_anim = new AnimatorSet();
        collaps_anim.playTogether(
                ObjectAnimator.ofFloat(btdevice_list, btdevice_list.SCALE_X, 0.85f),
                ObjectAnimator.ofFloat(btdevice_list, btdevice_list.SCALE_Y, 0.85f),
                ObjectAnimator.ofFloat(btdevice_list, btdevice_list.ALPHA, 0.75f)
        );
        collaps_anim.setDuration(250);
        collaps_anim.setInterpolator(new AccelerateInterpolator());

        AnimatorSet slide_anim = new AnimatorSet();
        slide_anim.playTogether(
                ObjectAnimator.ofFloat(btdevice_list, btdevice_list.X, btdevice_list.getHeight())
        );
        slide_anim.setDuration(250);
        slide_anim.setInterpolator(new AnticipateInterpolator());

        AnimatorSet hide_anim = new AnimatorSet();
        hide_anim.playSequentially(
                collaps_anim,
                slide_anim
        );
        hide_anim.addListener(hideAnimatorListener);

        return hide_anim;
    }

    private AnimatorSet getShowConnectAnimation(){

        AnimatorListenerAdapter showAnimatorListener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                dialog_progress.setVisibility(View.VISIBLE);
                dialog_progress.setProgress(1);
                dialog_message.setText(getString(R.string.messageDeviceConnect, last_device.name));
                dialog_message.setVisibility(View.VISIBLE);
                dialog_title.setText(getString(R.string.textFormatPercent, (float)dialog_progress.getProgress()/BT_CONNECT_ATTEMPS *100));
                dialog_title.setVisibility(View.VISIBLE);
            }
        };

        AnimatorSet show_anim = new AnimatorSet();
        show_anim.playTogether(
                ObjectAnimator.ofFloat(dialog_progress, dialog_progress.SCALE_X, 1f),
                ObjectAnimator.ofFloat(dialog_title, dialog_title.TRANSLATION_Y, -160),
                ObjectAnimator.ofFloat(dialog_title, dialog_title.ALPHA, 1f),
                ObjectAnimator.ofFloat(dialog_message, dialog_message.ALPHA, 1f)
        );
        show_anim.setDuration(350);
        show_anim.setInterpolator(new DecelerateInterpolator());
        show_anim.addListener(showAnimatorListener);

        return show_anim;
    }

    private AnimatorSet getHideConnectAnimation() {

        AnimatorListenerAdapter hideAnimatorListener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                dialog_progress.setVisibility(View.INVISIBLE);
                dialog_progress.setProgress(0);
                dialog_message.setVisibility(View.INVISIBLE);
                dialog_title.setVisibility(View.INVISIBLE);
            }
        };

        AnimatorSet hide_anim = new AnimatorSet();
        hide_anim.playTogether(
                ObjectAnimator.ofFloat(dialog_progress, dialog_progress.SCALE_X, 0.1f),
                ObjectAnimator.ofFloat(dialog_title, dialog_title.TRANSLATION_Y, 0),
                ObjectAnimator.ofFloat(dialog_title, dialog_title.ALPHA, 0f),
                ObjectAnimator.ofFloat(dialog_message, dialog_message.ALPHA, 0f)
        );
        hide_anim.setDuration(350);
        hide_anim.setInterpolator(new AccelerateInterpolator());
        hide_anim.addListener(hideAnimatorListener);

        return hide_anim;
    }

    private AnimatorSet getShowDialogAnimation(final int idTitle,
                                               final int idMessage,
                                               final int id_pButton,
                                               final int id_nButton,
                                               final View.OnClickListener pButtonListener,
                                               final View.OnClickListener nButtonListener){

        AnimatorListenerAdapter showAnimatorListener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationEnd(animation);

                dialog_image.setVisibility(View.VISIBLE);
                dialog_title.setText(idTitle);
                dialog_title.setVisibility(View.VISIBLE);
                dialog_message.setText(idMessage);
                dialog_message.setVisibility(View.VISIBLE);
                dialog_positive.setText(id_pButton);
                dialog_positive.setOnClickListener(pButtonListener);
                dialog_positive.setVisibility(View.VISIBLE);
                dialog_negative.setText(id_nButton);
                dialog_negative.setOnClickListener(nButtonListener);
                dialog_negative.setVisibility(View.VISIBLE);
            }
        };

        AnimatorSet show_image_anim = new AnimatorSet();
        show_image_anim.playTogether(
                ObjectAnimator.ofFloat(dialog_image, dialog_image.SCALE_X, 1f),
                ObjectAnimator.ofFloat(dialog_image, dialog_image.SCALE_Y, 1f),
                ObjectAnimator.ofFloat(dialog_image, dialog_image.ALPHA, 1f)
        );
        show_image_anim.setInterpolator(new OvershootInterpolator());

        AnimatorSet show_text_anim = new AnimatorSet();
        show_text_anim.playTogether(
                ObjectAnimator.ofFloat(dialog_title, dialog_title.ALPHA, 1f),
                ObjectAnimator.ofFloat(dialog_message, dialog_message.ALPHA, 1f),
                ObjectAnimator.ofFloat(dialog_positive, dialog_positive.ALPHA, 1f),
                ObjectAnimator.ofFloat(dialog_negative, dialog_negative.ALPHA, 1f)
        );
        show_text_anim.setInterpolator(new DecelerateInterpolator());

        AnimatorSet show_anim = new AnimatorSet();
        show_anim.playTogether(
                show_image_anim,
                show_text_anim
        );
        show_anim.setDuration(350);
        show_anim.addListener(showAnimatorListener);

        return show_anim;
    }

    private AnimatorSet getHideDialogAnimation(){

        AnimatorListenerAdapter hideAnimatorListener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                dialog_image.setVisibility(View.INVISIBLE);
                dialog_title.setVisibility(View.INVISIBLE);
                dialog_message.setVisibility(View.INVISIBLE);
                dialog_positive.setVisibility(View.INVISIBLE);
                dialog_negative.setVisibility(View.INVISIBLE);
            }
        };

        AnimatorSet hide_image_anim = new AnimatorSet();
        hide_image_anim.playTogether(
                ObjectAnimator.ofFloat(dialog_image, dialog_image.SCALE_X, 0.1f),
                ObjectAnimator.ofFloat(dialog_image, dialog_image.SCALE_Y, 0.1f),
                ObjectAnimator.ofFloat(dialog_image, dialog_image.ALPHA, 0f)
        );
        hide_image_anim.setInterpolator(new AnticipateInterpolator());

        AnimatorSet hide_text_anim = new AnimatorSet();
        hide_text_anim.playTogether(
                ObjectAnimator.ofFloat(dialog_title, dialog_title.ALPHA, 0f),
                ObjectAnimator.ofFloat(dialog_message, dialog_message.ALPHA, 0f),
                ObjectAnimator.ofFloat(dialog_positive, dialog_positive.ALPHA, 0f),
                ObjectAnimator.ofFloat(dialog_negative, dialog_negative.ALPHA, 0f)
        );
        hide_text_anim.setInterpolator(new AccelerateInterpolator());

        AnimatorSet hide_anim = new AnimatorSet();
        hide_anim.playTogether(
                hide_image_anim,
                hide_text_anim
        );
        hide_anim.setDuration(350);
        hide_anim.addListener(hideAnimatorListener);

        return hide_anim;
    }

}
