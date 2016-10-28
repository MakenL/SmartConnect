package com.embedhome.btlamp.wake;

public class btDevice {
    public String addr;
    public String name;
    public boolean paried;
    public boolean avaible;
    public boolean favorite;

    public btDevice(String addr, String name, boolean paried, boolean avaible, boolean favorite) {
        this.addr = addr;
        this.name = name;
        this.paried = paried;
        this.avaible = avaible;
        this.favorite = favorite;
    }

    @Override
    public String toString() {

        return name;
    }
}


