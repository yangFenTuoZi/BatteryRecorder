package android.os;

import android.os.BatteryProperty;

interface IBatteryPropertiesRegistrar {
    int getProperty(in int id, out BatteryProperty prop);
}