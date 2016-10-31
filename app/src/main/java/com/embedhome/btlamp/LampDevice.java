package com.embedhome.btlamp;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.TextView;

public class LampDevice extends Fragment {

    private int page_number;
    private static final String ARG_SECTION_NUMBER = "section";

    private ImageView lamp_enable;
    private TextView lamp_battary;
    private TextView lamp_timer;
    private Button lamp_button;
    private SeekBar red_seekbar;
    private SeekBar green_seekbar;
    private SeekBar blue_seekbar;
    private Button color_button;

    private NumberPicker hour_ind;
    private NumberPicker min_ind;
    private Button settimer_button;

    private OnEventListener listeren;

    public LampDevice() {

    }

    public static LampDevice newInstance(int sectionNumber) {
        LampDevice fragment = new LampDevice();
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        page_number = getArguments().getInt(ARG_SECTION_NUMBER);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = null;

        switch (page_number) {
            case 0:
                rootView = inflater.inflate(R.layout.fragment_status, container, false);
                lamp_enable = (ImageView) rootView.findViewById(R.id.lamp_enable_ind);
                lamp_battary = (TextView) rootView.findViewById(R.id.lamp_battary_ind);
                lamp_timer = (TextView) rootView.findViewById(R.id.lamp_timer_ind);
                lamp_button = (Button) rootView.findViewById(R.id.lamp_enable_button);
                lamp_button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listeren.onClickEnableButton();
                    }
                });
                break;
            case 1:
                rootView = inflater.inflate(R.layout.fragment_color, container, false);
                red_seekbar = (SeekBar) rootView.findViewById(R.id.lamp_red_seekbar);
                green_seekbar = (SeekBar) rootView.findViewById(R.id.lamp_green_seekbar);
                blue_seekbar = (SeekBar) rootView.findViewById(R.id.lamp_blue_seekbar);
                color_button = (Button) rootView.findViewById(R.id.lamp_color_button);
                color_button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listeren.onSetColor(red_seekbar.getProgress(), green_seekbar.getProgress(), blue_seekbar.getProgress());
                    }
                });
                break;
            case 2:
                rootView = inflater.inflate(R.layout.fragment_timer, container, false);
                hour_ind = (NumberPicker) rootView.findViewById(R.id.lamp_hour_timer);
                hour_ind.setMinValue(0);
                hour_ind.setMaxValue(4);
                hour_ind.setFormatter(new NumberPicker.Formatter() {
                    @Override
                    public String format(int value) {
                        return String.format(getResources().getString(R.string.textFormatHour), value);
                    }
                });
                hour_ind.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
                hour_ind.setValue(0);

                min_ind = (NumberPicker) rootView.findViewById(R.id.lamp_min_timer);
                min_ind.setMinValue(0);
                min_ind.setMaxValue(59);
                min_ind.setFormatter(new NumberPicker.Formatter() {
                    @Override
                    public String format(int value) {
                        return String.format(getResources().getString(R.string.textFormatMin), value);
                    }
                });
                min_ind.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
                min_ind.setValue(0);

                settimer_button = (Button) rootView.findViewById(R.id.lamp_settimer_button);
                settimer_button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listeren.onSetTimer(hour_ind.getValue(), min_ind.getValue());
                    }
                });
                break;
        }

        listeren = (OnEventListener) getActivity();

        return rootView;

    }

    @Override
    public void onAttach(Context context) {

        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }


    public void setEnable(int enable){
        if (page_number == 0) {
            if (enable == 0){
                lamp_enable.setImageResource(R.drawable.imageLampDisable);
                lamp_button.setText(R.string.actionTextEnable);
            } else {
                lamp_enable.setImageResource(R.drawable.imageLampEnable);
                lamp_button.setText(R.string.actionTextDisable);
            }
        }
    }

    public void setPower(int status, int power){
        if (page_number == 0) {

            switch (status){
                case 0:
                    // Питание от батареи
                    lamp_battary.setText(getString(R.string.textFormatPowerBattery, (float)power/10));
                    break;
                case 1:
                    // Питание от сети/зарядка завершена
                    lamp_battary.setText(getString(R.string.textFormatPower, (float)power/10));
                    break;
                default:
                    // Питание от сети/идет зарядка
                    lamp_battary.setText(getString(R.string.textFormatPowerCharging, (float)power/10));
                    break;
            }

            if (power > 35) {
                lamp_battary.setTextColor(getActivity().getResources().getColor(R.color.colorTextPrimary));
            } else {
                lamp_battary.setTextColor(getActivity().getResources().getColor(R.color.colorTextWarning));
            }
        }

    }

    public void setTimer(int hour, int min){
        if (page_number == 0) {
            lamp_timer.setText(getString(R.string.textFormatTimer, hour, min));
            if ((hour == 0) && (min == 0)){
                lamp_timer.setTextColor(getActivity().getResources().getColor(R.color.colorTextInactive));
            } else {
                lamp_timer.setTextColor(getActivity().getResources().getColor(R.color.colorTextPrimary));
            }
        } else if (page_number == 2) {
            hour_ind.setValue(hour);
            min_ind.setValue(min);
        }
    }

    public void setColor(int red, int green, int blue){
        if (page_number == 1) {
            red_seekbar.setProgress(red);
            green_seekbar.setProgress(green);
            blue_seekbar.setProgress(blue);
        }
    }



    public interface OnEventListener {

        void onClickEnableButton();
        void onSetColor(int red, int green, int blue);
        void onSetTimer(int hour, int min);
    }


}
