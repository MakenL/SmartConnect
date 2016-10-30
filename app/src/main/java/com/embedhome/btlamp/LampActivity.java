package com.embedhome.btlamp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.TabLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.embedhome.btlamp.wake.BluetoothWake;
import com.embedhome.btlamp.wake.btDevice;

public class LampActivity extends AppCompatActivity implements BluetoothWake.OnEventListener, LampDevice.OnEventListener{


    private SectionsPagerAdapter mSectionsPagerAdapter;
    private ViewPager mViewPager;
    private TabLayout tabLayout;

    private BluetoothWake btWake;
    private checkStatusDeviceThread checkStatusDeviceTask;
    private String device_name = new String();
    private String device_addr = new String();
    private String device_ver = new String();

    private volatile int property_enable = 0;
    private volatile int property_power = 0;
    private volatile int property_status = 0;
    private volatile int property_red = 33;
    private volatile int property_green = 33;
    private volatile int property_blue = 33;
    private volatile int property_hour = -1;
    private volatile int property_min = -1;

    private volatile int property_tx = 1;
    private volatile int property_rx = 1;
    private volatile int property_err = 0;

    private AppNotification app_notification;

    private LinearLayout llBottomSheet;
    private BottomSheetBehavior bottomSheetBehavior;
    private RelativeLayout bottomSheetBar;
    private RelativeLayout bottomSheetData;
    private ImageView bottomSheetImage;

    private ImageView dialog_image;
    private TextView dialog_title;
    private TextView dialog_message;
    private Button dialog_positive;
    private Button dialog_negative;
    private CheckBox dialog_bt;

    private TextView lamp_tx;
    private TextView lamp_rx;
    private TextView lamp_err;

    private static final int BT_RESULT_DISCONNECT = 0;
    private static final int BT_RESULT_ERROR = 1;
    private static final int BT_RESULT_DISABLE = 2;

    private static final int BT_EVENT_SEND_PACKAGE = 0;
    private static final int BT_MAX_ERR_PACKAGE = 10;

    private Handler btConnection_h = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case BT_EVENT_SEND_PACKAGE:
                    sendWakePackage();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);

        Intent intent = getIntent();
        device_name = intent.getStringExtra("name");
        device_addr = intent.getStringExtra("addr");
        device_ver = intent.getStringExtra("version");

        Toolbar toolbar = (Toolbar) findViewById(R.id.lamp_toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(device_name);
        }

        TextView lamp_name = (TextView)findViewById(R.id.lamp_about_name);
        lamp_name.setText(getString(R.string.textDeviceName, device_name));
        TextView lamp_addr = (TextView)findViewById(R.id.lamp_about_addr);
        lamp_addr.setText(getString(R.string.textDeviceAddr, device_addr));
        TextView lamp_version = (TextView)findViewById(R.id.lamp_about_version);
        lamp_version.setText(getString(R.string.textDeviceVer, device_ver));

        btWake = BluetoothWake.getInstance();
        btWake.setEventListener(this);

        app_notification = new AppNotification(this);

        mViewPager = (ViewPager) findViewById(R.id.lamp_container);
        mViewPager.setPageTransformer(true, new ZoomOutPageTransformer());
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
        mViewPager.setAdapter(mSectionsPagerAdapter);
        mViewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            }

            @Override
            public void onPageSelected(int position) {
                switch (position){
                    case 0:
                        ((LampDevice)mSectionsPagerAdapter.getRegisteredFragment(mViewPager.getCurrentItem())).setEnable(property_enable);
                        ((LampDevice)mSectionsPagerAdapter.getRegisteredFragment(mViewPager.getCurrentItem())).setPower(property_status, property_power);
                        ((LampDevice)mSectionsPagerAdapter.getRegisteredFragment(mViewPager.getCurrentItem())).setTimer(property_hour, property_min);
                        break;
                    case 1:
                        ((LampDevice)mSectionsPagerAdapter.getRegisteredFragment(mViewPager.getCurrentItem())).setColor(property_red, property_green, property_blue);
                        break;
                    case 2:
                        ((LampDevice)mSectionsPagerAdapter.getRegisteredFragment(mViewPager.getCurrentItem())).setTimer(property_hour, property_min);
                        break;
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        tabLayout = (TabLayout) findViewById(R.id.lamp_tabs);
        tabLayout.setupWithViewPager(mViewPager);

        lamp_tx = (TextView)findViewById(R.id.lamp_about_tx_packeges);
        lamp_tx.setText(getString(R.string.textTxPackages, property_tx));
        lamp_rx = (TextView)findViewById(R.id.lamp_about_rx_packeges);
        lamp_rx.setText(getString(R.string.textRxPackages, property_rx));
        lamp_err = (TextView)findViewById(R.id.lamp_about_err_packeges);
        lamp_err.setText(getString(R.string.textErrPackages, property_err));

        bottomSheetBar = (RelativeLayout) findViewById(R.id.lamp_about_bar);
        bottomSheetData = (RelativeLayout) findViewById(R.id.lamp_about_layout);
        bottomSheetImage = (ImageView) findViewById(R.id.lamp_about_barimage);

        // получение вью нижнего экрана
        llBottomSheet = (LinearLayout) findViewById(R.id.lamp_bottom_sheet);
        // настройка поведения нижнего экрана
        bottomSheetBehavior = BottomSheetBehavior.from(llBottomSheet);
        bottomSheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback(){

            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {

            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {

                if (slideOffset >= 0) {

                    float[] hsvEnd = new float[3];
                    float[] hsvStart = new float[3];
                    float[] hsvColor = new float[3];

                    Color.colorToHSV(getResources().getColor(R.color.colorPaletteBlueLight), hsvEnd);
                    Color.colorToHSV(getResources().getColor(R.color.colorPaletteGrey), hsvStart);

                    float dH = hsvEnd[0] - hsvStart[0];
                    float dS = hsvEnd[1] - hsvStart[1];
                    float dV = hsvEnd[2] - hsvStart[2];

                    hsvColor[0] = hsvStart[0] + dH * slideOffset;
                    hsvColor[1] = hsvStart[1] + dS * slideOffset;
                    hsvColor[2] = hsvStart[2] + dV * slideOffset;

                    bottomSheetImage.setRotation(180 * slideOffset);

                    bottomSheetBar.setBackgroundColor(Color.HSVToColor(hsvColor));
                    bottomSheetData.setAlpha(slideOffset);
                }
            }
        });

        dialog_image = (ImageView) findViewById(R.id.lamp_dialog_image);
        dialog_title = (TextView) findViewById(R.id.lamp_dialog_title);
        dialog_message = (TextView) findViewById(R.id.lamp_dialog_message);
        dialog_bt = (CheckBox) findViewById(R.id.lamp_dialog_bluetooth);

        dialog_positive = (Button) findViewById(R.id.lamp_dialog_pbutton);
        dialog_positive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                returnDevicePager();
            }
        });

        dialog_negative = (Button) findViewById(R.id.lamp_dialog_nbutton);
        dialog_negative.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {

                if (dialog_bt.isChecked()) {
                    btWake.sendPackage(btWake.WAKE_ADDR_NO, btWake.WAKE_CMD_COMOFF, 0, null);
                }
                btWake.disconnectDevice();
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.menu_device, menu);

        MenuItem item = menu.findItem(R.id.menu_lamp_notification);
        switch (app_notification.getNotification()) {
            case AppNotification.NOTIFICATION_DISABLE:
                item.setIcon(R.drawable.imageMenuNotificationSoundOff);
                break;
            case AppNotification.NOTIFICATION_RINGTON:
                item.setIcon(R.drawable.imageMenuNotificationSoundOn);
                break;
            case AppNotification.NOTIFICATION_VIBRO:
                item.setIcon(R.drawable.imageMenuNotificationVibration);
                break;
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case android.R.id.home:
                if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                } else {
                    if (mViewPager.getVisibility() == View.VISIBLE){
                        showDisconnectDialog();
                    }
                }
                return true;
            case R.id.menu_lamp_notification:
                switch (app_notification.getNotification()) {
                    case AppNotification.NOTIFICATION_DISABLE:
                        app_notification.setNotification(AppNotification.NOTIFICATION_RINGTON);
                        item.setIcon(R.drawable.imageMenuNotificationSoundOn);
                        app_notification.show();
                        break;
                    case AppNotification.NOTIFICATION_RINGTON:
                        app_notification.setNotification(AppNotification.NOTIFICATION_VIBRO);
                        item.setIcon(R.drawable.imageMenuNotificationVibration);
                        app_notification.show();
                        break;
                    case AppNotification.NOTIFICATION_VIBRO:
                        app_notification.setNotification(AppNotification.NOTIFICATION_DISABLE);
                        item.setIcon(R.drawable.imageMenuNotificationSoundOff);
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
        checkStatusDeviceTask = new checkStatusDeviceThread();
        checkStatusDeviceTask.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (checkStatusDeviceTask.isAlive()){
            checkStatusDeviceTask.interrupt();
        }
    }

    @Override
    public void onBackPressed() {

        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        } else {
            if (mViewPager.getVisibility() == View.VISIBLE){
                showDisconnectDialog();
            };
        }
    }

    @Override
    public void onStartDiscovery() {

    }

    @Override
    public void onCancelDiscovery() {

    }

    @Override
    public void onFindDevice(btDevice device) {

    }

    @Override
    public void onConnect() {

    }

    @Override
    public void onDisconnect() {
        Intent intent = new Intent();
        setResult(BT_RESULT_DISCONNECT, intent);
        finish();
    }

    @Override
    public void onConnectError() {

    }

    @Override
    public void onConnectEnableError() {
        Intent intent = new Intent();
        setResult(BT_RESULT_DISABLE, intent);
        finish();
    }

    @Override
    public void onConnectIoError() {
        Intent intent = new Intent();
        setResult(BT_RESULT_ERROR, intent);
        finish();
    }

    @Override
    public void onRxPackage(int cmd, int nbt, int[] data) {

        if (cmd == btWake.WAKE_CMD_STATUS) {
            if (nbt == 8) {
                if (data != null) {
                    // Если состояние включения изменилось
                    if (property_enable != data[0]) {
                        property_enable = data[0];
                        // Вызываем метод обработки изменения состояния
                        ((LampDevice)mSectionsPagerAdapter.getRegisteredFragment(mViewPager.getCurrentItem())).setEnable(property_enable);
                    }
                    // Если изменилось напряжение питания или источник питания
                    if ((property_power != data[1]) || (property_status != data[2])){
                        property_power = data[1];
                        property_status = data[2];
                        // Вызываем метод обработки изменения состояния
                        ((LampDevice)mSectionsPagerAdapter.getRegisteredFragment(mViewPager.getCurrentItem())).setPower(property_status, property_power);
                    }
                    // Если яркость изменилась
                    if ((property_red != data[3]) || (property_green != data[4]) || (property_blue != data[5])) {
                        property_red = data[3];
                        property_green = data[4];
                        property_blue = data[5];
                        // Вызываем медот обработки изменения состояния
                        ((LampDevice)mSectionsPagerAdapter.getRegisteredFragment(mViewPager.getCurrentItem())).setColor(property_red, property_green, property_blue);
                    }
                    // Если значение таймера изменилась
                    if ((property_hour != data[6]) || (property_min != data[7])) {
                        property_hour = data[6];
                        property_min = data[7];
                        // Вызываем метод обработки изменения состояния
                        ((LampDevice)mSectionsPagerAdapter.getRegisteredFragment(mViewPager.getCurrentItem())).setTimer(property_hour, property_min);
                    }
                } else {
                    receiveErrWakePackage();
                }
            } else {
                receiveErrWakePackage();
            }
        } else if (cmd == btWake.WAKE_CMD_ERR) {
            receiveErrWakePackage();
        }
        receiveWakePackage();

        if (property_err > BT_MAX_ERR_PACKAGE) {
            btWake.disconnectDevice();
        }
    }

    @Override
    public void onClickEnableButton() {
        btWake.sendPackage(btWake.WAKE_ADDR_NO, btWake.WAKE_CMD_ENABLE, 0, null);
        app_notification.show();
        sendWakePackage();
    }

    @Override
    public void onSetColor(int red, int green, int blue) {

        int[] colors = new int[3];
        colors[0] = red;
        colors[1] = green;
        colors[2] = blue;

        btWake.sendPackage(btWake.WAKE_ADDR_NO, btWake.WAKE_CMD_SETCLR, 3, colors);
        app_notification.show();
        sendWakePackage();
    }

    @Override
    public void onSetTimer(int hour, int min) {
        int[] times = new int[2];
        times[0] = hour;
        times[1] = min;

        btWake.sendPackage(btWake.WAKE_ADDR_NO, btWake.WAKE_CMD_SETTMR, 2, times);
        app_notification.show();
        sendWakePackage();
    }

    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        SparseArray<Fragment> registeredFragments = new SparseArray<Fragment>();

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {

            return LampDevice.newInstance(position);
        }

        @Override
        public int getCount() {

            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getResources().getString(R.string.textStatus);
                case 1:
                    return getResources().getString(R.string.textColor);
                case 2:
                    return getResources().getString(R.string.textTimer);
            }
            return null;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            registeredFragments.put(position, fragment);
            return fragment;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            registeredFragments.remove(position);
            super.destroyItem(container, position, object);
        }

        public Fragment getRegisteredFragment(int position) {
            return registeredFragments.get(position);
        }
    }

    public class ZoomOutPageTransformer implements ViewPager.PageTransformer {
        private static final float MIN_SCALE = 0.85f;
        private static final float MIN_ALPHA = 0.5f;

        public void transformPage(View view, float position) {
            int pageWidth = view.getWidth();
            int pageHeight = view.getHeight();

            if (position < -1) { // [-Infinity,-1)
                // This page is way off-screen to the left.
                view.setAlpha(0);

            } else if (position <= 1) { // [-1,1]
                // Modify the default slide transition to shrink the page as well
                float scaleFactor = Math.max(MIN_SCALE, 1 - Math.abs(position));
                float vertMargin = pageHeight * (1 - scaleFactor) / 2;
                float horzMargin = pageWidth * (1 - scaleFactor) / 2;
                if (position < 0) {
                    view.setTranslationX(horzMargin - vertMargin / 2);
                } else {
                    view.setTranslationX(-horzMargin + vertMargin / 2);
                }

                // Scale the page down (between MIN_SCALE and 1)
                view.setScaleX(scaleFactor);
                view.setScaleY(scaleFactor);

                // Fade the page relative to its size.
                view.setAlpha(MIN_ALPHA +
                        (scaleFactor - MIN_SCALE) /
                                (1 - MIN_SCALE) * (1 - MIN_ALPHA));

            } else { // (1,+Infinity]
                // This page is way off-screen to the right.
                view.setAlpha(0);
            }
        }
    }

    private void sendWakePackage(){
        property_tx++;
        lamp_tx.setText(getString(R.string.textTxPackages, property_tx));

    }

    private void receiveWakePackage(){
        property_rx++;
        lamp_rx.setText(getString(R.string.textRxPackages, property_rx));
    }

    private void receiveErrWakePackage(){
        property_err++;
        lamp_err.setText(getString(R.string.textErrPackages, property_err));
    }

    private class checkStatusDeviceThread extends Thread {

        @Override
        public void run() {

            do {
                // Запрос статуса
                btWake.sendPackage(btWake.WAKE_ADDR_NO, btWake.WAKE_CMD_STATUS, 0, null);
                btConnection_h.sendEmptyMessage(BT_EVENT_SEND_PACKAGE);
                //Проверка прерывания
                if(Thread.interrupted())
                {
                    break;
                } else {
                    // Приостановка потока на 1000 мсек
                    try{
                        this.sleep(1000);
                    } catch(InterruptedException e){
                        // Завершение потока после прерывания
                        break;
                    }
                }

            } while (true);
        }
    }

    private void showDisconnectDialog(){

        // воспроизводим анимацию
        AnimatorSet anim = new AnimatorSet();
        anim.playSequentially(
                getHideDevicePagerAnimation(),
                getShowDialogAnimation()
        );
        anim.start();
    }

    private void returnDevicePager(){

        // воспроизводим анимацию
        AnimatorSet anim = new AnimatorSet();
        anim.playSequentially(
                getHideDialogAnimation(),
                getShowDevicePagerAnimation()
        );
        anim.start();
    }

    private AnimatorSet getShowDevicePagerAnimation(){

        AnimatorListenerAdapter showAnimatorListener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                mViewPager.setVisibility(View.VISIBLE);
                tabLayout.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                bottomSheetBehavior.setHideable(false);
            }
        };

        AnimatorSet expand_anim = new AnimatorSet();
        expand_anim.playTogether(
                ObjectAnimator.ofFloat(mViewPager, mViewPager.SCALE_X, 1f),
                ObjectAnimator.ofFloat(mViewPager, mViewPager.SCALE_Y, 1f),
                ObjectAnimator.ofFloat(mViewPager, mViewPager.ALPHA, 1f)
        );
        expand_anim.setDuration(250);
        expand_anim.setInterpolator(new OvershootInterpolator());

        AnimatorSet slide_anim = new AnimatorSet();
        slide_anim.playTogether(
                ObjectAnimator.ofFloat(mViewPager, mViewPager.X, 0),
                ObjectAnimator.ofFloat(tabLayout, tabLayout.X, 0)
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

    private AnimatorSet getHideDevicePagerAnimation(){

        AnimatorListenerAdapter hideAnimatorListener = new AnimatorListenerAdapter() {

            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);

                mViewPager.setVisibility(View.INVISIBLE);
                tabLayout.setVisibility(View.INVISIBLE);

                bottomSheetBehavior.setHideable(true);
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            }
        };

        AnimatorSet collaps_anim = new AnimatorSet();
        collaps_anim.playTogether(
                ObjectAnimator.ofFloat(mViewPager, mViewPager.SCALE_X, 0.85f),
                ObjectAnimator.ofFloat(mViewPager, mViewPager.SCALE_Y, 0.85f),
                ObjectAnimator.ofFloat(mViewPager, mViewPager.ALPHA, 0.75f)
        );
        collaps_anim.setDuration(250);
        collaps_anim.setInterpolator(new AccelerateInterpolator());

        AnimatorSet slide_anim = new AnimatorSet();
        slide_anim.playTogether(
                ObjectAnimator.ofFloat(mViewPager, mViewPager.X, mViewPager.getHeight()),
                ObjectAnimator.ofFloat(tabLayout, tabLayout.X, tabLayout.getHeight())
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

    private AnimatorSet getShowDialogAnimation(){

        AnimatorListenerAdapter showAnimatorListener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationEnd(animation);

                dialog_image.setVisibility(View.VISIBLE);
                dialog_title.setVisibility(View.VISIBLE);
                dialog_message.setVisibility(View.VISIBLE);
                dialog_positive.setVisibility(View.VISIBLE);
                dialog_negative.setVisibility(View.VISIBLE);
                dialog_bt.setVisibility(View.VISIBLE);
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
                ObjectAnimator.ofFloat(dialog_negative, dialog_negative.ALPHA, 1f),
                ObjectAnimator.ofFloat(dialog_bt, dialog_bt.ALPHA, 1f)
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
                dialog_bt.setVisibility(View.INVISIBLE);
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
                ObjectAnimator.ofFloat(dialog_negative, dialog_negative.ALPHA, 0f),
                ObjectAnimator.ofFloat(dialog_bt, dialog_bt.ALPHA, 0f)
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
