package android.os;

public class BatteryProperty implements Parcelable {
    private long mValueLong;
    private String mValueString;

    public BatteryProperty() {
        mValueLong = Long.MIN_VALUE;
        mValueString = null;
    }

    public long getLong() {
        return mValueLong;
    }

    public String getString() {
        return mValueString;
    }

    public void setLong(long val) {
        mValueLong = val;
    }

    public void setString(String val) {
        mValueString = val;
    }

    private BatteryProperty(Parcel p) {
        readFromParcel(p);
    }

    public void readFromParcel(Parcel p) {
        mValueLong = p.readLong();
        mValueString = p.readString();
    }

    public void writeToParcel(Parcel p, int flags) {
        p.writeLong(mValueLong);
        p.writeString(mValueString);
    }

    public static final Parcelable.Creator<BatteryProperty> CREATOR
            = new Parcelable.Creator<>() {
        public BatteryProperty createFromParcel(Parcel p) {
            return new BatteryProperty(p);
        }

        public BatteryProperty[] newArray(int size) {
            return new BatteryProperty[size];
        }
    };

    public int describeContents() {
        return 0;
    }
}