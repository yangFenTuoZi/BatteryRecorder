package android.content;

import android.os.Bundle;
import android.os.IInterface;
import android.os.RemoteException;

import androidx.annotation.Nullable;

public interface IContentProvider extends IInterface {

    Bundle call(AttributionSource attributionSource, String authority, String method, @Nullable String arg, @Nullable Bundle extras)
            throws RemoteException;
}
