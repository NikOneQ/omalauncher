package android.window;

import android.os.IInterface;
import android.os.RemoteException;
import android.view.SurfaceControl;

/** Compile-time stand-in; see IRemoteTransition. */
public interface IRemoteTransitionFinishedCallback extends IInterface {
    void onTransitionFinished(WindowContainerTransaction wct, SurfaceControl.Transaction sct) throws RemoteException;
}
