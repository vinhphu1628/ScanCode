package com.example.xfoodz.scancode.MVVM.VM;

import android.util.Log;

import com.example.xfoodz.scancode.MVVM.View.NPNHomeView;
import com.example.xfoodz.scancode.Network.ApiResponseListener;

/**
 * Created by Le Trong Nhan on 19/06/2018.
 */

public class NPNHomeViewModel extends BaseViewModel<NPNHomeView> {
    public void updateToServer(String url)
    {
        Log.d("Debug", "aaaa");
        requestGETWithURL(url, new ApiResponseListener<String>() {
            @Override
            public void onSuccess(String response) {
                view.onSuccessUpdateServer(response);
            }

            @Override
            public void onError(String error) {
                view.onErrorUpdateServer(error);
            }
        });
    }
}

