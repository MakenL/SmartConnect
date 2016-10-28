package com.embedhome.btlamp;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.embedhome.btlamp.wake.btDevice;

import java.util.ArrayList;
import java.util.List;

public class btDeviceAdapter extends RecyclerView.Adapter<btDeviceAdapter.ViewHolder> {

    private List<btDevice> mDevices = new ArrayList<btDevice>();
    public static OnSelectDeviceListener mListener;


    public interface OnSelectDeviceListener {
        void onSelectDevice(btDevice item);
    }

    public btDeviceAdapter(OnSelectDeviceListener listener) {

        mDevices.clear();
        mListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.btdevice_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mItem = mDevices.get(position);
        holder.mAddr.setText(holder.mItem.addr);
        holder.mName.setText(holder.mItem.name);

        if (holder.mItem.paried){

            if (holder.mItem.avaible){
                holder.mAvaiblee.setImageResource(R.drawable.imageConnectRecycledAvaible);
                holder.mAddr.setTextColor(holder.mAddr.getResources().getColor(R.color.colorRecycledItemAddr));
                holder.mName.setTextColor(holder.mName.getResources().getColor(R.color.colorRecycledItemAvaible));
            } else {
                holder.mAvaiblee.setImageResource(R.drawable.imageConnectRecycledNotAvaible);
                holder.mAddr.setTextColor(holder.mAddr.getResources().getColor(R.color.colorRecycledItemNotAvaible));
                holder.mName.setTextColor(holder.mName.getResources().getColor(R.color.colorRecycledItemNotAvaible));
            }

        } else {
            holder.mAddr.setTextColor(holder.mAddr.getResources().getColor(R.color.colorRecycledItemAddr));
            holder.mName.setTextColor(holder.mName.getResources().getColor(R.color.colorRecycledItemFound));
            holder.mAvaiblee.setImageResource(R.drawable.imageConnectRecycledOther);
        }

        holder.mView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mListener) {
                    mListener.onSelectDevice(holder.mItem);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return mDevices.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView mAddr;
        public final TextView mName;
        public final ImageView mAvaiblee;
        public btDevice mItem;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mName = (TextView) view.findViewById(R.id.btdevice_name);
            mAddr = (TextView) view.findViewById(R.id.btdevice_addr);
            mAvaiblee = (ImageView) view.findViewById(R.id.btdevice_avaible);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mName.getText() + "'";
        }
    }

    public void addItem (btDevice item){
        int position = mDevices.size();
        mDevices.add(item);
        super.notifyItemInserted(position);
    }

    public void setPariedDevice(btDevice item){

        int position = mDevices.size();

        while (position > 0){

            position--;
            if (mDevices.get(position).addr.equalsIgnoreCase(item.addr)){
                mDevices.get(position).paried = true;
                mDevices.get(position).avaible = true;
                super.notifyItemChanged(position);
                return;
            }
        }

    }

    public void clearItems (){
        mDevices.clear();
        super.notifyDataSetChanged();
    }

    public void clearFoundItems(){

        int position = mDevices.size();

        while (position > 0){

            position--;

            if (!mDevices.get(position).paried){
                mDevices.remove(position);
                super.notifyItemRemoved(position);
            }
        }
    }
}
