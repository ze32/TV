package com.fongmi.android.tv.utils;

import com.android.cast.dlna.dmc.DLNACastManager;
import com.fongmi.android.tv.bean.Device;

import java.util.ArrayList;
import java.util.List;

public class DLNADevice {

    private final List<org.fourthline.cling.model.meta.Device<?, ?, ?>> devices;

    private static class Loader {
        static volatile DLNADevice INSTANCE = new DLNADevice();
    }

    public static DLNADevice get() {
        return Loader.INSTANCE;
    }

    public DLNADevice() {
        this.devices = new ArrayList<>();
    }

    public List<com.fongmi.android.tv.bean.Device> getAll() {
        List<com.fongmi.android.tv.bean.Device> items = new ArrayList<>();
        for (org.fourthline.cling.model.meta.Device<?, ?, ?> item : devices) items.add(Device.get(item));
        return items;
    }

    public List<com.fongmi.android.tv.bean.Device> add(org.fourthline.cling.model.meta.Device<?, ?, ?> item) {
        devices.remove(item);
        devices.add(item);
        return getAll();
    }

    public Device remove(org.fourthline.cling.model.meta.Device<?, ?, ?> device) {
        devices.remove(device);
        return Device.get(device);
    }

    public void disconnect() {
        for (org.fourthline.cling.model.meta.Device<?, ?, ?> device : devices) DLNACastManager.INSTANCE.disconnectDevice(device);
    }

    public org.fourthline.cling.model.meta.Device<?, ?, ?> find(com.fongmi.android.tv.bean.Device item) {
        for (org.fourthline.cling.model.meta.Device<?, ?, ?> device : devices) if (device.getIdentity().getUdn().getIdentifierString().equals(item.getUuid())) return device;
        return null;
    }
}
